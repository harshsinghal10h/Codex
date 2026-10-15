package com.harsh.vlcglass;
import android.graphics.*;
import android.view.TextureView;
import org.json.*;
/** VASO convex bezel optics on LIVE video, Android 13+. Separate RGB rays
 * model dispersion. Bounds checks skip work outside visible glass surfaces. */
public final class NativeLens {
 private static RuntimeShader shader;
 private static float clamp(double n,float lo,float hi){return (float)Math.max(lo,Math.min(hi,n));}
 public static void apply(TextureView view,JSONObject a,JSONObject settings,int rootW,int rootH){
  if(!a.optBoolean("active")||a.optJSONArray("rects")==null||rootW<=0||rootH<=0){view.setRenderEffect(null);return;}
  try{
   if(shader==null){String s="uniform shader image; uniform float depth; uniform float dispersion; uniform float frost; uniform float highlight; uniform float2 light;";
    for(int i=0;i<12;i++)s+="uniform float4 r"+i+"; uniform float radius"+i+";";
    s+="float4 lens(float2 p,float4 r,float radius){float2 halfsize=r.zw*.5;float bezel=min(halfsize.x,halfsize.y)*.6;float rad=min(max(radius,bezel),min(halfsize.x,halfsize.y));float2 v=p-r.xy-halfsize;float2 q=abs(v)-(halfsize-rad);float d;float2 n;if(q.x>0.0&&q.y>0.0){float len=length(q);d=rad-len;n=q/len;}else if(q.x>q.y){d=rad-q.x;n=float2(1,0);}else{d=rad-q.y;n=float2(0,1);}n*=sign(v);if(d<0.0)return float4(0);float t=clamp(d/max(bezel,1.0),0.0,1.0);float u=1.0-t;float slope=(u*u*u)/pow(max(1.0-u*u*u*u,0.000001),.75);float incident=atan(slope);float profile=tan(incident-asin(sin(incident)/1.5))/1.118034;return float4(-n*profile*bezel*.35*depth,profile,max(0.0,dot(n,light)));}";
    s+="half4 main(float2 p){float4 field=float4(0);";
    for(int i=0;i<12;i++)s+="if(r"+i+".z>0.0&&p.x>=r"+i+".x&&p.y>=r"+i+".y&&p.x<=r"+i+".x+r"+i+".z&&p.y<=r"+i+".y+r"+i+".w){float4 f=lens(p,r"+i+",radius"+i+");if(f.z>field.z)field=f;}";
    s+="if(field.z<.0001)return image.eval(p);float2 off=field.xy;float2 spectral=off*dispersion*.055;float2 pos=p+off;half4 c=image.eval(pos);if(dispersion>.01)c=half4(image.eval(pos-spectral).r,c.g,image.eval(pos+spectral).b,c.a);if(frost>.01){half4 soft=(image.eval(pos+float2(frost,0))+image.eval(pos-float2(frost,0))+image.eval(pos+float2(0,frost))+image.eval(pos-float2(0,frost)))*.25;c=mix(c,soft,half(.28));}float rim=pow(field.z,2.0)*highlight*(.018+.075*field.w);return half4(min(c.rgb+half3(rim),half3(1)),c.a);}";shader=new RuntimeShader(s);
   }
   boolean wave="lastwave".equals(settings.optString("glassMode","apple"));float sx=(float)(rootW/a.optDouble("width",1)),sy=(float)(rootH/a.optDouble("height",1)),zx=view.getScaleX(),zy=view.getScaleY(),px=view.getPivotX(),py=view.getPivotY();JSONArray rects=a.getJSONArray("rects");
   shader.setFloatUniform("depth",clamp(settings.optDouble("glassDepth",1),.2f,2f)*(wave?1.6f:1.05f));shader.setFloatUniform("dispersion",clamp(settings.optDouble("glassDispersion",.55),0,1.5f)*(wave?.7f:.38f));shader.setFloatUniform("frost",clamp(settings.optDouble("glassFrost",1),0,3)*(wave?3.5f:.7f)*sx/zx);shader.setFloatUniform("highlight",clamp(settings.optDouble("glassHighlight",.75),0,1));shader.setFloatUniform("light",-.6f,-.8f);
   for(int i=0;i<12;i++){JSONObject r=rects.optJSONObject(i);shader.setFloatUniform("r"+i,r==null?0:((float)r.optDouble("x")*sx-view.getLeft()-px)/zx+px,r==null?0:((float)r.optDouble("y")*sy-view.getTop()-py)/zy+py,r==null?0:(float)r.optDouble("w")*sx/zx,r==null?0:(float)r.optDouble("h")*sy/zy);shader.setFloatUniform("radius"+i,r==null?0:(float)r.optDouble("radius",24)*sx/zx);}view.setRenderEffect(RenderEffect.createRuntimeShaderEffect(shader,"image"));
  }catch(Exception e){view.setRenderEffect(null);}
 }
}
