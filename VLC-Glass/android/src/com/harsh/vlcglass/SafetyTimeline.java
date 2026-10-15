package com.harsh.vlcglass;

/** Immutable skip schedule. No allocation, model inference or I/O in target(). */
public final class SafetyTimeline {
 public final long[] starts,ends;
 public final long duration;
 public SafetyTimeline(long[] starts,long[] ends,long duration){
  if(starts.length!=ends.length||duration<=0)throw new IllegalArgumentException("Invalid timeline");
  this.starts=starts.clone();this.ends=ends.clone();this.duration=duration;
  long previous=-1;
  for(int i=0;i<starts.length;i++){
   if(starts[i]<0||starts[i]<=previous||ends[i]<=starts[i]||ends[i]>duration)throw new IllegalArgumentException("Invalid interval");
   previous=ends[i];
  }
 }
 public long target(long position,float speed){
  long ahead=(long)(200*Math.max(.25,Math.min(4,speed)));
  int lo=0,hi=starts.length-1,index=-1;
  while(lo<=hi){int mid=(lo+hi)>>>1;if(starts[mid]<=position+ahead){index=mid;lo=mid+1;}else hi=mid-1;}
  return index>=0&&position<ends[index]?ends[index]:-1;
 }
}
