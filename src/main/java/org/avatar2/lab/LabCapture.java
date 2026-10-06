package org.avatar2.lab;

import org.lwjgl.opengl.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;

/** On-demand game framebuffer only; never captures other desktop applications. */
final class LabCapture {
    static final AtomicBoolean requested=new AtomicBoolean();
    private static final AtomicBoolean busy=new AtomicBoolean();
    private static final ExecutorService encoder=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"Avatar2 PNG encoder");t.setDaemon(true);return t;});
    static volatile CapturedFrame latest;
    private static volatile Map<String,Object> metadata=LabJson.map("status","no capture requested");
    private static volatile long generation;
    static void clear(){generation++;requested.set(false);latest=null;metadata=LabJson.map("status","no capture requested");}
    static Map<String,Object> status(){CapturedFrame frame=latest;return frame==null?metadata:frame.metadata;}
    static final class CapturedFrame {
        final byte[] png;final Map<String,Object> metadata;
        CapturedFrame(byte[] png,Map<String,Object> metadata){this.png=png;this.metadata=metadata;}
    }
    static void onRender(Map<String,Object> telemetry){
        if(!requested.get()||!busy.compareAndSet(false,true))return;
        requested.set(false);
        final long captureGeneration=generation;
        try{
            int width=Display.getWidth(),height=Display.getHeight();
            if(width<=0||height<=0||(long)width*height>8_500_000)throw new IllegalStateException("Unsupported framebuffer size");
            final ByteBuffer pixels=ByteBuffer.allocateDirect(width*height*4);
            int read=GL11.glGetInteger(GL11.GL_READ_BUFFER),alignment=GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
            int row=GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH),skipRows=GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS),skipPixels=GL11.glGetInteger(GL11.GL_PACK_SKIP_PIXELS);
            boolean pbo=GLContext.getCapabilities().OpenGL21;
            int pack=pbo?GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING):0;
            boolean fbo=GLContext.getCapabilities().OpenGL30;
            int framebuffer=fbo?GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING):0;
            try{
                if(pbo)GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,0);
                if(fbo)GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,0);
                GL11.glReadBuffer(GL11.GL_BACK);GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,1);
                GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH,0);GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS,0);GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS,0);
                GL11.glReadPixels(0,0,width,height,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
            }finally{
                if(fbo)GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,framebuffer);
                GL11.glReadBuffer(read);GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,alignment);
                GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH,row);GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS,skipRows);GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS,skipPixels);
                if(pbo)GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,pack);
            }
            // Associate this capture with the published pose; engine scheduling is
            // not a GPU fence. The companion waits two settled render frames.
            final Map<String,Object> pose=telemetry;
            encoder.execute(()->{
                try{
                    BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
                    for(int y=0;y<height;y++)for(int x=0;x<width;x++){
                        int i=((height-1-y)*width+x)*4;image.setRGB(x,y,((pixels.get(i)&255)<<16)|((pixels.get(i+1)&255)<<8)|(pixels.get(i+2)&255));
                    }
                    ByteArrayOutputStream output=new ByteArrayOutputStream();ImageIO.write(image,"png",output);
                    Map<String,Object> meta=LabJson.map("status","ready","timeMillis",System.currentTimeMillis(),"width",width,"height",height,"sequence",pose.get("sequence"),"revision",pose.get("revision"));
                    if(captureGeneration==generation)latest=new CapturedFrame(output.toByteArray(),meta);
                }catch(Exception error){if(captureGeneration==generation){latest=null;metadata=LabJson.map("status","failed","error",error.toString());}}finally{busy.set(false);}
            });
        }catch(Throwable error){latest=null;metadata=LabJson.map("status","failed","error",error.toString());busy.set(false);}
    }
}
