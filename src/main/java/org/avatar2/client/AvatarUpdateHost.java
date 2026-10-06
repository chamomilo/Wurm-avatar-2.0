package org.avatar2.client;

import com.wurmonline.client.renderer.gui.*;
import org.chamomilo.wurm.update.*;
import java.awt.Desktop;
import java.net.URI;
import java.util.List;
import java.util.logging.*;

/** Shared protocol-1 catalogue host; downloads require an explicit user click. */
final class AvatarUpdateHost implements SharedUpdateCoordinator.Host {
    private static final Logger LOG=Logger.getLogger("Avatar2.Updates");
    private HeadsUpDisplay hud;
    private List<ModUpdate> updates;
    private ChamomiloUpdateWindow window;
    private boolean shown;
    void hudReady(HeadsUpDisplay next){hud=next;show();SharedUpdateCoordinator.startOnce();}
    @Override public void updatesReady(List<ModUpdate> value){Avatar2Mod.deferUi(()->{updates=value;show();});}
    @Override public void checkFailed(String repository,Throwable failure){LOG.log(Level.WARNING,"Update check failed: "+repository,failure);}
    private void show(){
        if(hud==null||shown||updates==null||updates.isEmpty())return;
        try{
            final HudBridge bridge=new HudBridge();
            window=new ChamomiloUpdateWindow(hud,updates,"Chamomilo updates","Versions of installed mods",
                    "Download","Open release ZIP for manual installation","Close","Close catalogue",
                    update->{try{Desktop.getDesktop().browse(URI.create(update.getDownloadUrl()));}catch(Exception e){LOG.log(Level.WARNING,"Could not open release",e);}},
                    ()->Avatar2Mod.deferUi(()->{try{bridge.remove(hud,window);window=null;}catch(Exception e){LOG.log(Level.WARNING,"Could not close catalogue",e);}}));
            bridge.add(hud,window);shown=true;
        }catch(Throwable failure){LOG.log(Level.WARNING,"Could not show shared update catalogue",failure);}
    }
}
