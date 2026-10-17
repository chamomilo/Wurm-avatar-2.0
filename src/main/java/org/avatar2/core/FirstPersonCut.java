package org.avatar2.core;

import java.util.*;

/** Immutable topology cut at the lower neck, welding UV seams only for boundary discovery. */
public final class FirstPersonCut {
    private FirstPersonCut() { }
    public static final class Result {
        public final int[] indices;
        public final int removedTriangles,capTriangles;
        Result(int[] indices,int removed,int cap) { this.indices=indices;removedTriangles=removed;capTriangles=cap; }
    }
    public static Result build(float[] vertices,int[] indices,float cutY) {
        int[] canonical=new int[vertices.length/3];Map<String,Integer> weld=new HashMap<>();
        for(int v=0;v<canonical.length;v++) {
            String key=Math.round(vertices[v*3]*100000)+":"+Math.round(vertices[v*3+1]*100000)+":"+Math.round(vertices[v*3+2]*100000);
            Integer id=weld.get(key);if(id==null) { id=v;weld.put(key,id); }canonical[v]=id;
        }
        Map<Long,Edge> all=new HashMap<>(),kept=new HashMap<>();List<Integer> output=new ArrayList<>();
        int removed=0;
        for(int i=0;i<indices.length;i+=3) {
            int a=indices[i],b=indices[i+1],c=indices[i+2];
            edge(all,a,b,canonical);edge(all,b,c,canonical);edge(all,c,a,canonical);
            if(vertices[a*3+1]>cutY || vertices[b*3+1]>cutY || vertices[c*3+1]>cutY) { removed++;continue; }
            output.add(a);output.add(b);output.add(c);
            edge(kept,a,b,canonical);edge(kept,b,c,canonical);edge(kept,c,a,canonical);
        }
        Map<Integer,Edge> boundary=new LinkedHashMap<>();
        for(Map.Entry<Long,Edge> entry:kept.entrySet()) {
            Edge e=entry.getValue();
            if(e.count==1 && all.get(entry.getKey()).count==2) {
                if(boundary.put(canonical[e.a],e)!=null) throw new IllegalStateException("Branched first-person neck boundary");
            }
        }
        int cap=0,loops=0;
        while(!boundary.isEmpty()) {
            int start=boundary.keySet().iterator().next(),next=start;List<Integer> ring=new ArrayList<>();
            do {
                Edge e=boundary.remove(next);if(e==null) throw new IllegalStateException("Open first-person neck boundary");
                ring.add(e.a);next=canonical[e.b];
            } while(next!=start);
            // Reverse boundary winding closes the retained body with existing skinned vertices.
            for(int i=1;i<ring.size()-1;i++) { output.add(ring.get(0));output.add(ring.get(i+1));output.add(ring.get(i));cap++; }
            loops++;
        }
        if(removed==0 || loops==0 || cap==0) throw new IllegalStateException("No closed neck cut found");
        int[] result=new int[output.size()];for(int i=0;i<result.length;i++) result[i]=output.get(i);
        return new Result(result,removed,cap);
    }
    private static void edge(Map<Long,Edge> edges,int a,int b,int[] canonical) {
        int ca=canonical[a],cb=canonical[b];if(ca==cb) return;
        long key=((long)Math.min(ca,cb)<<32)|(Math.max(ca,cb)&0xffffffffL);
        Edge e=edges.get(key);if(e==null) edges.put(key,new Edge(a,b));else e.count++;
    }
    private static final class Edge { final int a,b;int count=1;Edge(int a,int b) { this.a=a;this.b=b; } }
}
