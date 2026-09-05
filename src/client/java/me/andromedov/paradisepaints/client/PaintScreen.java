package me.andromedov.paradisepaints.client;

import java.util.UUID;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

public final class PaintScreen extends Screen {
    private final UUID session;
    private final CanvasModel canvas;
    private final int[] palette;
    private int left,top,scale,color=34,brush=1,tool=0,lastX,lastY,ticks;
    private boolean drawing;
    private final SaveAttempt saveAttempt=new SaveAttempt();
    private String status="";
    private Button saveButton, closeButton;
    public PaintScreen(UUID session,byte[] pixels,int[] palette) {
        super(Component.literal("ParadisePaints")); this.session=session;
        this.canvas=new CanvasModel(pixels); this.palette=palette;
    }
    @Override protected void init() {
        scale= Math.clamp((width - 150) / 128, 1, (height - 85) / 128);
        left=12; top=24;
        int x=left+128*scale+10, y=top;
        addRenderableWidget(Button.builder(text("pencil"), ignored->{ if(!saveAttempt.frozen()) tool=0; }).bounds(x,y,112,20).build());
        addRenderableWidget(Button.builder(text("eraser"), ignored->{ if(!saveAttempt.frozen()) tool=1; }).bounds(x,y+22,112,20).build());
        addRenderableWidget(Button.builder(text("fill"), ignored->{ if(!saveAttempt.frozen()) tool=2; }).bounds(x,y+44,112,20).build());
        addRenderableWidget(Button.builder(text("brush",brush), b->{
            if(saveAttempt.frozen()) return;
            brush=brush==1?2:brush==2?4:brush==4?8:1; b.setMessage(text("brush",brush));
        }).bounds(x,y+66,112,20).build());
        addRenderableWidget(Button.builder(text("undo"), ignored->{ if(!saveAttempt.frozen()) canvas.undo(); }).bounds(x,y+88,112,20).build());
        addRenderableWidget(Button.builder(text("redo"), ignored->{ if(!saveAttempt.frozen()) canvas.redo(); }).bounds(x,y+110,112,20).build());
        saveButton=addRenderableWidget(Button.builder(text("save"), ignored->onClose()).bounds(x,y+132,112,20).build());
        closeButton=addRenderableWidget(Button.builder(text("close"), ignored->{
            if(saveAttempt.frozen() && !saveAttempt.waiting()) minecraft.gui.setScreen(null);
        }).bounds(x,y+154,112,20).build());
        updateButtons();
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta) {
        g.fill(0,0,width,height,0xff20232a);
        g.text(font,"ParadisePaints · "+text(new String[]{"pencil","eraser","fill"}[tool]).getString(),12,8,0xffffffff);
        for(int y=0;y<128;y++) {
            int x=0;
            while(x<128) {
                int index=canvas.color(x,y), end=x+1;
                while(end<128 && canvas.color(end,y)==index) end++;
                g.fill(left+x*scale,top+y*scale,left+end*scale,top+(y+1)*scale,
                    index<4?0xffeee4cf:palette[index]);
                x=end;
            }
        }
        int py=top+128*scale+6;
        for(int i=4;i<248;i++) {
            int px=left+((i-4)%61)*4, row=(i-4)/61;
            g.fill(px,py+row*8,px+4,py+row*8+7,palette[i]);
        }
        g.text(font,(status.isEmpty()?text("color",color):text(status)).getString(),left,py+34,0xffffffff);
        if(saveAttempt.frozen() && !saveAttempt.waiting()) g.text(font,text("close-warning").getString(),left,py+44,0xffffcc66);
        super.extractRenderState(g,mouseX,mouseY,delta);
    }
    @Override public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if(saveAttempt.frozen()) return super.mouseClicked(event,doubleClick);
        double mx=event.x(),my=event.y();
        int py=top+128*scale+6;
        if(mx>=left && mx<left+244 && my>=py && my<py+32) {
            color=4+(int)(mx-left)/4+((int)(my-py)/8)*61; return true;
        }
        if(inside(mx,my)) {
            int x=(int)(mx-left)/scale,y=(int)(my-top)/scale;
            canvas.beginStroke(); lastX=x; lastY=y;
            if(tool==2) canvas.fill(x,y,color);
            else { drawing=true; canvas.line(x,y,x,y,brush,tool==1?0:color); }
            return true;
        }
        return super.mouseClicked(event,doubleClick);
    }
    @Override public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
        if(drawing && !saveAttempt.frozen() && inside(event.x(),event.y())) {
            int x=(int)(event.x()-left)/scale,y=(int)(event.y()-top)/scale;
            canvas.line(lastX,lastY,x,y,brush,tool==1?0:color); lastX=x; lastY=y; return true;
        }
        return super.mouseDragged(event,dx,dy);
    }
    @Override public boolean mouseReleased(@NonNull MouseButtonEvent event) { drawing=false; return super.mouseReleased(event); }
    private boolean inside(double x,double y) { return x>=left && y>=top && x<left+128*scale && y<top+128*scale; }
    @Override public void tick() {
        if(!saveAttempt.frozen() && ++ticks%100==0) ParadisepaintsClient.save(session,canvas.pixels(),false);
        if(saveAttempt.tick()) { status="timeout"; updateButtons(); }
    }
    @Override public void onClose() {
        if(!saveAttempt.waiting()) {
            drawing=false; status="saving";
            ParadisepaintsClient.save(session,saveAttempt.begin(canvas.pixels()),true);
            updateButtons();
        }
    }
    public void acknowledge(UUID id,boolean ok) {
        if(!session.equals(id)||!saveAttempt.frozen()) return;
        saveAttempt.rejected();
        if(ok) minecraft.gui.setScreen(null);
        else { status="rejected"; updateButtons(); }
    }
    private void updateButtons() {
        saveButton.active=!saveAttempt.waiting();
        saveButton.setMessage(text(saveAttempt.frozen()?"retry":"save"));
        closeButton.visible=saveAttempt.frozen() && !saveAttempt.waiting();
    }
    private static Component text(String key,Object... args) {
        return Component.translatable("paradisepaints.editor."+key,args);
    }
    @Override public boolean isPauseScreen() { return false; }
}
