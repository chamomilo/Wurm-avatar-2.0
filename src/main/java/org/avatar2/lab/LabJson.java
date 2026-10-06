package org.avatar2.lab;

import java.util.*;

/** Small bounded JSON codec. No client-library dependency or executable input. */
public final class LabJson {
    private LabJson() { }
    public static Object parse(String input) {
        Parser p = new Parser(input); Object result = p.value(0); p.white();
        if (p.at != input.length()) throw new IllegalArgumentException("Trailing JSON data");
        return result;
    }
    @SuppressWarnings("unchecked") public static Map<String,Object> object(Object value) {
        if (!(value instanceof Map)) throw new IllegalArgumentException("Expected JSON object");
        return (Map<String,Object>) value;
    }
    public static Map<String,Object> map(Object... pairs) {
        Map<String,Object> result = new LinkedHashMap<String,Object>();
        for (int i=0;i<pairs.length;i+=2) result.put((String)pairs[i], pairs[i+1]);
        return result;
    }
    public static String write(Object value) { StringBuilder out=new StringBuilder(); append(out,value); return out.toString(); }
    private static void append(StringBuilder out,Object value) {
        if (value==null) out.append("null");
        else if (value instanceof String) {
            out.append('"'); for(char c:((String)value).toCharArray()) {
                if(c=='"'||c=='\\') out.append('\\').append(c);
                else if(c<32) out.append(String.format(Locale.ROOT,"\\u%04x",(int)c));
                else out.append(c);
            } out.append('"');
        } else if(value instanceof Number) {
            double d=((Number)value).doubleValue(); out.append(Double.isFinite(d)?value:"null");
        } else if(value instanceof Boolean) out.append(value);
        else if(value instanceof Map) {
            out.append('{'); boolean first=true;
            for(Object e0:((Map<?,?>)value).entrySet()) {
                Map.Entry<?,?> e=(Map.Entry<?,?>)e0; if(!first) out.append(','); first=false;
                append(out,e.getKey().toString()); out.append(':'); append(out,e.getValue());
            } out.append('}');
        } else if(value instanceof Iterable) {
            out.append('['); boolean first=true; for(Object v:(Iterable<?>)value) {
                if(!first) out.append(','); first=false; append(out,v);
            } out.append(']');
        } else if(value.getClass().isArray()) {
            out.append('['); int length=java.lang.reflect.Array.getLength(value);
            for(int i=0;i<length;i++){if(i>0)out.append(',');append(out,java.lang.reflect.Array.get(value,i));}out.append(']');
        } else throw new IllegalArgumentException("Not JSON: "+value.getClass());
    }
    private static final class Parser {
        final String in; int at;
        Parser(String in){if(in.length()>262144)throw new IllegalArgumentException("JSON exceeds 256 KiB");this.in=in;}
        void white(){while(at<in.length()&&" \r\n\t".indexOf(in.charAt(at))>=0)at++;}
        Object value(int depth){
            if(depth>32)throw new IllegalArgumentException("JSON nesting exceeds 32");white();
            if(at>=in.length())throw bad(); char c=in.charAt(at);
            if(c=='"')return string();
            if(c=='{'){
                at++;Map<String,Object> m=new LinkedHashMap<String,Object>();white();if(take('}'))return m;
                do{white();String key=string();white();if(!take(':'))throw bad();
                    if(m.containsKey(key))throw new IllegalArgumentException("Duplicate JSON key: "+key);
                    m.put(key,value(depth+1));white();if(take('}'))return m;if(!take(','))throw bad();
                }while(true);
            }
            if(c=='['){at++;List<Object> a=new ArrayList<Object>();white();if(take(']'))return a;
                do{a.add(value(depth+1));white();if(take(']'))return a;if(!take(','))throw bad();}while(true);}
            for(String literal:new String[]{"true","false","null"})if(in.startsWith(literal,at)){
                at+=literal.length();return literal.equals("null")?null:Boolean.valueOf(literal);
            }
            int start=at;while(at<in.length()&&"-+0123456789.eE".indexOf(in.charAt(at))>=0)at++;
            String n=in.substring(start,at);
            if(!n.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?"))throw bad();
            double d=Double.parseDouble(n);if(!Double.isFinite(d))throw bad();return d;
        }
        boolean take(char c){if(at<in.length()&&in.charAt(at)==c){at++;return true;}return false;}
        String string(){if(!take('"'))throw bad();StringBuilder b=new StringBuilder();
            while(at<in.length()){char c=in.charAt(at++);if(c=='"')return b.toString();if(c<32)throw bad();
                if(c=='\\'){if(at>=in.length())throw bad();c=in.charAt(at++);
                    if(c=='u'){if(at+4>in.length())throw bad();try{c=(char)Integer.parseInt(in.substring(at,at+4),16);}catch(NumberFormatException e){throw bad();}at+=4;}
                    else{int index="\"\\/bfnrt".indexOf(c);if(index<0)throw bad();c="\"\\/\b\f\n\r\t".charAt(index);}
                }b.append(c);
            }throw bad();
        }
        IllegalArgumentException bad(){return new IllegalArgumentException("Invalid JSON at character "+at);}
    }
}
