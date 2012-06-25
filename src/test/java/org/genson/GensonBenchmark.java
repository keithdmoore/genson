package org.genson;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.genson.GenericType;
import org.genson.Genson;
import org.genson.TransformationException;
import org.genson.bean.ComplexObject;
import org.genson.bean.Primitives;
import org.genson.reflect.TypeUtil;

import com.google.gson.Gson;

/*
 *======= Serialization Bench ======
Genson global serialization time=11.96 s
Genson avg serialization time=23.92 ms

Jackson global serialization time=7.307 s
Jackson avg serialization time=14.614 ms

Gson global serialization time=17.231 s
Gson avg serialization time=34.462 ms

======= Deserialization Bench ======
Genson global deserialization time=11.519 s
Genson avg deserialization time=23.038 ms

Jackson global deserialization time=9.024 s
Jackson avg deserialization time=18.048 ms

Gson global deserialization time=14.407 s
Gson avg deserialization time=28.814 ms

=================================

 */
public class GensonBenchmark {
	final int ITERATION_CNT = 500;
	private Genson genson = new Genson();
	private Gson gson = new Gson();
	private ObjectMapper om = new ObjectMapper();
	private Map<String, Object> map;
	private String json;

	public static void main(String[] args) throws JsonGenerationException, JsonMappingException, IOException, TransformationException {
		GensonBenchmark bench = new GensonBenchmark();
		bench.setUp();
		bench.benchSerialization();
		System.gc();
		synchronized (bench) {
			try {
				bench.wait(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		bench.benchDeserialization();
	}
	
	public void setUp() throws TransformationException {
		map = new HashMap<String, Object>();
		Primitives p1 = new Primitives(923456789, new Integer(56884646), 16737897023.96909986098180546, new Double(54657750.9988904315),
				"TEXT ...  HEY\\\"\\\"\\\"\\\"ads dd qdqsdq!", true, new Boolean(false));
		Primitives p2 = new Primitives(923456789, new Integer(861289603), 54566544.0998891, null, null, false, true);
	
		List<Primitives> longList = new ArrayList<Primitives>(100);
		for ( int i = 0; i < 50; i++ ) longList.add(p1);
		for ( int i = 50; i < 100; i++ ) longList.add(p2);
		List<Primitives> shortList = Arrays.asList(p1, p1, p2, p2);
		
		Primitives[] mediumArray = new Primitives[]{p1, p2, p1, p2, p2, p2, p1, p1, p2};
		
		ComplexObject coBig = new ComplexObject(p1, longList, mediumArray);
		ComplexObject coSmall = new ComplexObject(p1, shortList, mediumArray);
		ComplexObject coEmpty = new ComplexObject(null, new ArrayList<Primitives>(), new Primitives[]{});
		
		// Serialization object
		for (int i = 0; i < 100; i++) {
    		map.put("textValue"+i, "some text ...");
    		map.put("intValue"+i, 44542365);
    		map.put("doubleValue"+i, 7.5);
    		map.put("falseValue"+i, false);
    		map.put("trueValueObj"+i, Boolean.TRUE);
    		map.put("nullValue"+i, null);
    		
    		map.put("mediumArray"+i, mediumArray);
    		
    		map.put("complexBigObject"+i, coBig);
    		map.put("complexSmallObject"+i, coSmall);
    		map.put("complexEmptyObject"+i, coEmpty);
		}
		
		// Deserialization json string
		StringBuilder sb = new StringBuilder().append('{');
		int cnt = 100;
		for (int i = 0; i < cnt; i++) {
			sb.append("\"o1"+i+"\":").append(coBig.jsonString())
			.append(',')
			.append("\"o2"+i+"\":").append(coSmall.jsonString())
			.append(',')
			.append("\"o3"+i+"\":").append(coEmpty.jsonString());
			if ( i < (cnt-1) ) sb.append(',');
		}
		json = sb.append('}').toString();
	}
	
	/*
	 * Computer : dual core, 3.20GHz, 3.19GHz, 2 Go ram
	 * Iteration count: 50000
	 * 
	 * Genson global serialization time=17.718 s
	 * Genson avg serialization time=35.436 ms
	 * 
	 * Jackson global serialization time=11.135 s
	 * Jackson avg serialization time=22.27 ms
	 * 
	 * Gson global serialization time=20.43 s
	 * Gson avg serialization time=40.86 ms
	 */
	 public void benchSerialization() throws JsonGenerationException, JsonMappingException, IOException, TransformationException {
		// warm up
		for (int i = 0; i < 15; i++) {
			om.writeValueAsString(map);
			genson.serialize(map);
			gson.toJson(map);
		}
		
		System.out.println("======= Serialization Bench ======");
		
		@SuppressWarnings("unused")
		String dummyJson;
		Timer globalTimer = new Timer();
		Timer moyTimer = new Timer();
		
		globalTimer.start();
		moyTimer.start();
		for ( int i = 0; i < ITERATION_CNT; i++ ) {
			dummyJson = genson.serialize(map);
			moyTimer.cumulate();
		}
		System.out.println("Genson global serialization time="+globalTimer.stop().printS());
		System.out.println("Genson avg serialization time="+moyTimer.stop().printMS()+"\n");
//		
		globalTimer.start();
		moyTimer.start();
		for ( int i = 0; i < ITERATION_CNT; i++ ) {
			dummyJson = om.writeValueAsString(map);
			moyTimer.cumulate();
		}
		System.out.println("Jackson global serialization time="+globalTimer.stop().printS());
		System.out.println("Jackson avg serialization time="+moyTimer.stop().printMS()+"\n");

//		globalTimer.start();
//		moyTimer.start();
//		for ( int i = 0; i < ITERATION_CNT; i++ ) {
//			dummyJson = gson.toJson(map);
//			moyTimer.cumulate();
//		}
//		System.out.println("Gson global serialization time="+globalTimer.stop().printS());
//		System.out.println("Gson avg serialization time="+moyTimer.stop().printMS()+"\n");
		

	}
	
	/*
	 * Computer : dual core, 3.20GHz, 3.19GHz, 2 Go ram
	 * Iteration count: 50000
	 * 
	 * Genson global deserialization time=31.56 s
	 * Genson avg deserialization time=63.12 ms
	 * 
	 * Jackson global deserialization time=21.634 s
	 * Jackson avg deserialization time=43.268 ms
	 * 
	 * Gson global deserialization time=40.444 s
	 * Gson avg deserialization time=80.888 ms
	 * 
	 */
	 public void benchDeserialization() throws JsonGenerationException, JsonMappingException, IOException, TransformationException {
		// warm up
		Type type = new GenericType<Map<String, ComplexObject>>(){}.getType();
		@SuppressWarnings("unchecked")
		Class<Map<String, ComplexObject>> rawClass = (Class<Map<String, ComplexObject>>) TypeUtil.getRawClass(type);
		for (int i = 0; i < 15; i++) {
			om.readValue(json, rawClass);
			gson.fromJson(json, type);
			genson.deserialize(json, type);
		}
		
		System.out.println("======= Deserialization Bench ======");
		@SuppressWarnings("unused")
		Map<String, ComplexObject> cos;
		Timer globalTimer = new Timer();
		Timer moyTimer = new Timer();
		
		globalTimer.start();
		moyTimer.start();
		for ( int i = 0; i < ITERATION_CNT; i++ ) {
			cos = genson.deserialize(json, type);
			moyTimer.cumulate();
		}
		System.out.println("Genson global deserialization time="+globalTimer.stop().printS());
		System.out.println("Genson avg deserialization time="+moyTimer.stop().printMS()+"\n");
		
		
		globalTimer.start();
		moyTimer.start();
		for ( int i = 0; i < ITERATION_CNT; i++ ) {
			cos = om.readValue(json, rawClass);
			moyTimer.cumulate();
		}
		System.out.println("Jackson global deserialization time="+globalTimer.stop().printS());
		System.out.println("Jackson avg deserialization time="+moyTimer.stop().printMS()+"\n");

		globalTimer.start();
		moyTimer.start();
		for ( int i = 0; i < ITERATION_CNT; i++ ) {
			cos = gson.fromJson(json, type);
			moyTimer.cumulate();
		}
		System.out.println("Gson global deserialization time="+globalTimer.stop().printS());
		System.out.println("Gson avg deserialization time="+moyTimer.stop().printMS()+"\n");

		System.out.println("=================================");
	}
	 
	public static class Timer {
		private long start;
		private long end;
		private long sum;
		private int cnt;
		private boolean paused;
		
		public Timer start() {
			end = 0;
			sum = 0;
			cnt = 0;
			paused = false;
			start = System.currentTimeMillis();
			return this;
		}
		
		public Timer stop() {
			end = System.currentTimeMillis();
			
			return this;
		}
		
		public Timer pause() {
			paused = true;
			end = System.currentTimeMillis();
			return this;
		}
		
		public Timer unpause() {
			if ( paused ) {
				long t = System.currentTimeMillis();
				start = t - (end - start);
				paused = false;
			}
			return this;
		}
		
		public Timer cumulate() {
			cnt++;
			end = System.currentTimeMillis();
			sum += end - start;
			start = end;
			
			return this;
		}
		
		public String printMS() {
			if ( cnt > 0 )
				return ((double)sum/cnt) + " ms";
			
			return end-start + " ms";
		}
		
		public String printS() {
			if ( cnt > 0 )
				return ((double)sum/(cnt*1000)) + " ms";
			
			return ((double)(end-start)/1000) + " s";
		}
	}
}