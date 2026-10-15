"""Safe synthetic decoder fixtures; these do not evaluate classifier accuracy."""
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
from intelligence.vision import Scanner,_ffmpeg

class DecoderTest(unittest.TestCase):
 def setUp(self):
  self.temp=tempfile.TemporaryDirectory();self.root=Path(self.temp.name)
  self.video=self.root/'safe.mp4'
  subprocess.run([_ffmpeg(),'-loglevel','error','-y','-f','lavfi','-i','color=c=blue:s=96x64:r=24:d=1.17','-c:v','libx264','-pix_fmt','yuv420p',str(self.video)],check=True)
  self.model=self.root/'test-model';self.model.write_bytes(b'fixture model identity')
  self.scanner=Scanner(self.model,self.root/'maps')
  class Clear:
   def score(self,frame):return 0.0
  self.scanner.detector=Clear()
 def tearDown(self):self.temp.cleanup()
 def test_short_video_coverage_and_hash_bound_cache(self):
  result=self.scanner.scan(self.video)
  self.assertTrue(result['complete']);self.assertEqual(result['intervals'],[])
  self.assertGreater(result['duration'],1100)
  with patch('intelligence.vision._frames',side_effect=AssertionError('cached scan must not decode')):
   self.assertEqual(result,self.scanner.scan(self.video))
  self.model.write_bytes(b'changed model')
  with patch('intelligence.vision._frames',side_effect=RuntimeError('new model requires scan')):
   with self.assertRaisesRegex(RuntimeError,'requires scan'):self.scanner.scan(self.video)
 def test_positive_fixture_cannot_be_returned_as_clear(self):
  self.scanner.detector.score=lambda frame:.95
  result=self.scanner.scan(self.video)
  self.assertEqual(result['intervals'][0]['start'],0)
  self.assertEqual(result['intervals'][0]['end'],result['duration'])
 def test_incomplete_decoder_coverage_never_caches_a_complete_map(self):
  with patch('intelligence.vision._frames',return_value=iter([])):
   with self.assertRaisesRegex(ValueError,'complete video'):self.scanner.scan(self.video)
  self.assertEqual(list((self.root/'maps').glob('*.json')),[])
 def test_cancelled_scan_never_creates_a_map(self):
  with self.assertRaises(InterruptedError):self.scanner.scan(self.video,cancel=lambda:True)
  self.assertEqual(list((self.root/'maps').glob('*.json')),[])
if __name__=='__main__':unittest.main()
