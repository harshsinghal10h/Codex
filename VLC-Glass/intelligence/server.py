"""Authenticated companion for Android. Localhost by default; no arbitrary paths."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
import hmac
import json
import os
from pathlib import Path
import secrets
import ssl
import threading
from urllib.parse import unquote,urlsplit
from .sentry_adapter import SentryAdapter

MAX_VIDEO=16*1024**3

def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--bind',default='127.0.0.1');parser.add_argument('--port',type=int,default=8765)
    parser.add_argument('--cert');parser.add_argument('--key');parser.add_argument('--token-file',required=True)
    parser.add_argument('--data',default=str(Path.home()/'.vlc-glass-companion'))
    args=parser.parse_args(argv)
    if args.bind not in ('127.0.0.1','localhost','::1') and not (args.cert and args.key):parser.error('A LAN listener requires --cert and --key for HTTPS.')
    token_path=Path(args.token_file)
    if not token_path.exists():token_path.parent.mkdir(parents=True,exist_ok=True);token_path.write_text(secrets.token_urlsafe(32)+'\n');token_path.chmod(0o600)
    token=token_path.read_text().strip()
    if len(token)<24:parser.error('Use a token containing at least 24 characters.')
    root=Path(args.data);root.mkdir(parents=True,exist_ok=True)
    package=Path(__file__).resolve().parents[1]
    adapter=SentryAdapter(root/'index',next((package/'upstream/sentrysearch').glob('sentrysearch-*')))
    media={};backends={};registry=root/'media.json'
    if registry.exists():
        for id, value in json.loads(registry.read_text()).items():
            file=(root/Path(value['file']).name).resolve()
            if file.parent==root.resolve() and file.is_file():
                media[id]=str(file);backends[id]=value.get('backend','gemini')
    def persist():
        temporary=registry.with_suffix('.tmp')
        temporary.write_text(json.dumps({id:{'file':Path(file).name,'backend':backends.get(id,'gemini')} for id,file in media.items()}))
        temporary.replace(registry)
    jobs={};lock=threading.Lock();pool=ThreadPoolExecutor(max_workers=1);busy=threading.BoundedSemaphore(2)

    class Handler(BaseHTTPRequestHandler):
        protocol_version='HTTP/1.1'
        def log_message(self,*args):pass  # Do not log tokens, prompts, or filenames.
        def output(self,data,status=200):
            body=json.dumps(data).encode();self.send_response(status);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(body)));self.send_header('Cache-Control','no-store');self.end_headers();self.wfile.write(body)
        def authorized(self):
            # No browser CORS permission is granted. Clients authenticate on
            # every request, including reads and uploads.
            if not hmac.compare_digest(self.headers.get('Authorization',''),'Bearer '+token):self.close_connection=True;self.output({'error':'Unauthorized'},401);return False
            return True
        def read_json(self):
            size=int(self.headers.get('Content-Length','0'))
            if not 0<size<=16384:raise ValueError('Invalid JSON body size')
            return json.loads(self.rfile.read(size))
        def do_GET(self):
            if not self.authorized():return
            path=urlsplit(self.path).path
            if path.startswith('/v1/jobs/'):
                with lock:job=jobs.get(path.rsplit('/',1)[-1])
                self.output(job or {'error':'Job not found'},200 if job else 404)
            else:self.output({'error':'Not found'},404)
        def do_POST(self):
            if not self.authorized():return
            try:
                path=urlsplit(self.path).path
                if path=='/v1/media':
                    suffix=Path(unquote(self.headers.get('X-Filename','video.mp4'))).suffix.lower()
                    if suffix not in ('.mp4','.mkv','.avi','.webm','.mov','.ts','.m4v'):raise ValueError('Unsupported video container')
                    id=secrets.token_hex(16);file=root/(id+suffix);total=0;digest=hashlib.sha256()
                    try:
                        with open(file,'wb') as out:
                            chunked=self.headers.get('Transfer-Encoding','').lower()=='chunked'
                            remain=int(self.headers.get('Content-Length','0'))
                            if not chunked and not 0<remain<=MAX_VIDEO:raise ValueError('Invalid video size')
                            while True:
                                if chunked:
                                    line=self.rfile.readline(128)
                                    if not line.endswith(b'\r\n'):raise ValueError('Malformed upload')
                                    count=int(line.split(b';',1)[0],16)
                                    if count==0:
                                        if self.rfile.readline(128)!=b'\r\n':raise ValueError('Malformed upload trailer')
                                        break
                                    if not 0<count<=1024*1024:raise ValueError('Upload chunk too large')
                                else:
                                    if remain==0:break
                                    count=min(remain,65536)
                                total+=count
                                if total>MAX_VIDEO:raise ValueError('Video exceeds the companion size limit')
                                data=self.rfile.read(count)
                                if len(data)!=count:raise ValueError('Incomplete upload')
                                out.write(data);digest.update(data);remain-=count
                                if chunked and self.rfile.read(2)!=b'\r\n':raise ValueError('Malformed upload chunk')
                        if total==0:raise ValueError('Empty video')
                        with lock:
                            media[id]=str(file.resolve());persist()
                        self.output({'media_id':id,'media_sha256':digest.hexdigest()})
                    except Exception:file.unlink(missing_ok=True);raise
                elif path=='/v1/index':
                    data=self.read_json();id=str(data.get('media_id',''));backend=data.get('backend','gemini')
                    with lock:file=media.get(id)
                    if not file:raise ValueError('Upload the selected video first')
                    if backend not in ('gemini','local'):raise ValueError('Unsupported backend')
                    with lock:
                        backends[id]=backend;persist()
                    if not busy.acquire(blocking=False):self.output({'error':'The analysis queue is full. Try again later.'},429);return
                    job_id=secrets.token_hex(16)
                    with lock:jobs[job_id]={'phase':'indexing','progress':0}
                    def progress(value):
                        with lock:jobs[job_id]=value
                    def run():
                        try:progress(adapter.index(id,file,backend,progress))
                        except Exception as e:progress({'phase':'error','error':str(e)})
                        finally:busy.release()
                    pool.submit(run);self.output({'job_id':job_id},202)
                elif path=='/v1/search':
                    data=self.read_json();id=str(data.get('media_id',''))
                    with lock:exists=id in media
                    if not exists:raise ValueError('Unknown media ID')
                    indexed_backend=backends.get(id,'gemini')
                    if data.get('backend',indexed_backend)!=indexed_backend:raise ValueError('Prepare this video with the selected backend before searching.')
                    self.output(adapter.search(id,str(data.get('query','')),media[id],indexed_backend))
                else:self.output({'error':'Not found'},404)
            except (ValueError,OSError,KeyError) as e:self.close_connection=True;self.output({'error':str(e)},400)
            except Exception:self.close_connection=True;self.output({'error':'Analysis could not finish. Check the companion model and credentials.'},500)

    server=ThreadingHTTPServer((args.bind,args.port),Handler);server.daemon_threads=True
    if args.cert and args.key:
        context=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER);context.minimum_version=ssl.TLSVersion.TLSv1_2;context.load_cert_chain(args.cert,args.key);server.socket=context.wrap_socket(server.socket,server_side=True)
    try:server.serve_forever()
    finally:server.server_close();pool.shutdown(wait=False,cancel_futures=True)

if __name__=='__main__':main()
