package io.github.ninbyo02.lami.ui.screens.home;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.security.MessageDigest;
import android.os.Debug;
import org.json.*;
public final class StandaloneSentencePieceJni {
 public native long[] countPair(byte[] model,byte[] input,byte[] output);
 static volatile boolean sampling;
 static volatile long peakNative;
 public static void main(String[] args) throws Exception {
  long initialNative=Debug.getNativeHeapAllocatedSize();
  System.load(args[0]);
  var c=new StandaloneSentencePieceJni();
  for(int mi=2;mi<args.length;mi++) {
   long readStart=System.nanoTime();
   Class<?> reader=Class.forName("io.github.ninbyo02.lami.ui.screens.home.LitertLmSentencePieceSection");
   byte[] model=(byte[])reader.getMethod("read",java.io.File.class).invoke(reader.getField("INSTANCE").get(null),new java.io.File(args[mi]));
   long readNs=System.nanoTime()-readStart;
   StringBuilder hash=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(model))hash.append(String.format("%02x",b&255));
   JSONObject report=new JSONObject();report.put("model_index",mi-2);report.put("model_bytes",Files.size(Path.of(args[mi])));report.put("tokenizer_bytes",model.length);report.put("tokenizer_sha256",hash.toString());report.put("read_ns",readNs);
   JSONArray cases=new JSONArray();int index=0;
   for(String line:Files.readAllLines(Path.of(args[1]),StandardCharsets.UTF_8)) {
    String[] fields=line.split("\t",-1);byte[] text=Base64.getDecoder().decode(fields[0]);int expected=Integer.parseInt(fields[1]);
    long before=Debug.getNativeHeapAllocatedSize();peakNative=before;sampling=true;
    Thread sampler=new Thread(()->{while(sampling){peakNative=Math.max(peakNative,Debug.getNativeHeapAllocatedSize());try{Thread.sleep(1);}catch(Exception ignored){}}});sampler.start();
    long start=System.nanoTime();long[] counts;
    try{counts=c.countPair(model,text,text);}finally{sampling=false;sampler.join();}
    long elapsed=System.nanoTime()-start;
    if(counts.length!=4||counts[0]!=expected||counts[1]!=expected)throw new AssertionError("count mismatch: "+index);
    JSONObject result=new JSONObject();result.put("index",index++);result.put("input",counts[0]);result.put("output",counts[1]);result.put("load_ns",counts[2]);result.put("count_ns",counts[3]);result.put("elapsed_ns",elapsed);result.put("native_before",before);result.put("native_peak_sampled",peakNative);result.put("native_after",Debug.getNativeHeapAllocatedSize());cases.put(result);
   }
   for(int i=0;i<3;i++) {
    try{c.countPair(new byte[]{0},new byte[0],new byte[0]);throw new AssertionError("invalid accepted");}catch(IllegalStateException expected){}
    try{c.countPair(model,new byte[4*1024*1024+1],new byte[0]);throw new AssertionError("oversize accepted");}catch(IllegalStateException expected){}
    long[] recovered=c.countPair(model,new byte[0],new byte[0]);if(recovered[0]!=0||recovered[1]!=0)throw new AssertionError("recovery");
   }
   report.put("cases",cases);report.put("recovery_cycles",3);report.put("initial_native",initialNative);report.put("final_native",Debug.getNativeHeapAllocatedSize());
   System.out.println("TOKENIZER_DEVICE_PROBE "+report);
  }
 }
}
