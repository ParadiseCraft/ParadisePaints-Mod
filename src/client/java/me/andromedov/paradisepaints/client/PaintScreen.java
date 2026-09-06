package me.andromedov.paradisepaints.client;

import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;

public final class PaintScreen extends Screen {
    private static final String[] TOOLS={"pencil","eraser","fill","replace","line","picker"};
    private final UUID session;
    private final CanvasModel canvas;
    private final ColorPalette palette;
    private final int[] colors;
    private final List<Button> toolWidgets=new ArrayList<>(),colorWidgets=new ArrayList<>();
    private final Deque<Integer> recent=new ArrayDeque<>();
    private final SaveAttempt saveAttempt=new SaveAttempt();
    private int left,top,scale,panel,cell,gridY,color=34,brush=1,tool,lastX,lastY,startX,startY,ticks,page,tolerance;
    private boolean drawing,colorMenu;
    private String status="";
    private Button saveButton,closeButton,undoButton,redoButton,toolsTab,colorsTab;
    private EditBox hex;
    public PaintScreen(UUID session,byte[] pixels,int[] colors) {
        super(Component.literal("ParadisePaints")); this.session=session;
        canvas=new CanvasModel(pixels); palette=new ColorPalette(colors); this.colors=palette.colors(); recent.add(color);
    }
    @Override protected void init() {
        drawing=false; toolWidgets.clear(); colorWidgets.clear(); panel=width-154;
        scale=Math.max(1,Math.min((panel-16)/128,(height-96)/128));
        left=Math.max(6,(panel-128*scale)/2); top=Math.max(38,(height-64-128*scale)/2);
        toolsTab=addRenderableWidget(Button.builder(text("tools"),b->{ colorMenu=false; updateButtons(); }).bounds(panel,28,70,20).build());
        colorsTab=addRenderableWidget(Button.builder(text("colors"),b->{ colorMenu=true; updateButtons(); }).bounds(panel+74,28,70,20).build());
        for(int i=0;i<TOOLS.length;i++) {
            int selected=i;
            toolWidgets.add(addRenderableWidget(Button.builder(text(TOOLS[i]),b->{ tool=selected; updateButtons(); })
                    .bounds(panel+(i%2)*74,54+(i/2)*22,70,20).build()));
        }
        toolWidgets.add(addRenderableWidget(Button.builder(text("brush",brush),b->{
            brush=brush==16?1:brush*2; b.setMessage(text("brush",brush));
        }).bounds(panel,122,144,20).build()));
        toolWidgets.add(addRenderableWidget(Button.builder(text("tolerance",tolerance),b->{
            tolerance=tolerance==60?0:tolerance+15; b.setMessage(text("tolerance",tolerance));
        }).bounds(panel,144,144,20).build()));
        undoButton=addRenderableWidget(Button.builder(text("undo"),b->{ canvas.undo(); updateButtons(); }).bounds(panel,166,70,20).build());
        redoButton=addRenderableWidget(Button.builder(text("redo"),b->{ canvas.redo(); updateButtons(); }).bounds(panel+74,166,70,20).build());
        toolWidgets.add(undoButton); toolWidgets.add(redoButton);
        gridY=54; cell=Math.max(8,Math.min(17,(height-144)/8)); int below=gridY+cell*8+4;
        colorWidgets.add(addRenderableWidget(Button.builder(Component.literal("<"),b->{page=(page+3)%4;}).bounds(panel,below,30,18).build()));
        colorWidgets.add(addRenderableWidget(Button.builder(Component.literal(">"),b->{page=(page+1)%4;}).bounds(panel+114,below,30,18).build()));
        hex=addRenderableWidget(new EditBox(font,panel,below+24,144,18,text("hex")));
        hex.setMaxLength(7); hex.setValue(palette.hex(color));
        hex.setResponder(value->{
            var nearest=palette.nearest(value); hex.setTextColor(nearest.isPresent()?0xffffffff:0xffff7777);
            if(nearest.isPresent() && !saveAttempt.frozen()) select(nearest.getAsInt(),false);
        });
        saveButton=addRenderableWidget(Button.builder(text("save"),b->onClose()).bounds(panel,height-26,144,20).build());
        closeButton=addRenderableWidget(Button.builder(text("close"),b->{
            if(saveAttempt.frozen() && !saveAttempt.waiting()) minecraft.gui.setScreen(null);
        }).bounds(panel,height-48,144,20).build()); updateButtons();
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta) {
        g.fill(0,0,width,height,0xff171b22); g.fill(panel-6,22,width-4,height-4,0xff252c36);
        label(g,"ParadisePaints · "+text(TOOLS[tool]).getString(),10,10,width-20,0xfff4e7cf);
        frame(g,left-2,top-2,128*scale+4,128*scale+4,0xff8996a7);
        for(int y=0;y<128;y++) {
            int x=0;
            while(x<128) {
                int index=canvas.color(x,y),end=x+1;
                while(end<128 && canvas.color(end,y)==index) end++;
                g.fill(left+x*scale,top+y*scale,left+end*scale,top+(y+1)*scale,palette.argb(index)); x=end;
            }
        }
        if(!saveAttempt.frozen() && inside(mouseX,mouseY)) {
            int x=(mouseX-left)/scale,y=(mouseY-top)/scale;
            if(tool==4 && drawing) previewLine(g,startX,startY,x,y);
            else if(tool<2) {
                int x0=Math.max(0,x-brush/2),y0=Math.max(0,y-brush/2);
                int x1=Math.min(128,x-brush/2+brush),y1=Math.min(128,y-brush/2+brush);
                frame(g,left+x0*scale,top+y0*scale,(x1-x0)*scale,(y1-y0)*scale,0xffffffff);
            }
        }
        if(colorMenu && !saveAttempt.frozen()) {
            for(int i=0;i<64 && page*64+i<palette.size();i++) {
                int index=palette.at(page*64+i),x=panel+(i%8)*cell,y=gridY+(i/8)*cell;
                g.fill(x+1,y+1,x+cell-1,y+cell-1,palette.argb(index));
                if(index==color) frame(g,x,y,cell,cell,0xffffffff);
            }
            int below=gridY+cell*8+4;
            label(g,(page+1)+" / 4",panel+51,below+5,55,0xffc9d5e4);
            label(g,text("matched",palette.hex(color)).getString(),panel,below+46,144,0xffc9d5e4);
        }
        if(saveAttempt.frozen()) {
            int y=58;
            for(var line:font.split(text(status),144)) { g.text(font,line,panel,y,0xffc9d5e4); y+=11; }
            if(!saveAttempt.waiting()) {
                y+=12;
                for(var line:font.split(text("close-warning"),144)) { g.text(font,line,panel,y,0xffffcc66); y+=11; }
            }
        }
        int recentY=height-49,i=0;
        for(int index:recent) {
            int x=left+i++*15; g.fill(x,recentY,x+13,recentY+13,palette.argb(index));
            if(index==color) frame(g,x-1,recentY-1,15,15,0xffffffff);
        }
        label(g,status.isEmpty()?text("hint").getString():text(status).getString(),left,height-28,panel-left-10,0xffc9d5e4);
        if(saveAttempt.frozen() && !saveAttempt.waiting()) label(g,text("close-warning").getString(),left,height-16,panel-left-10,0xffffcc66);
        super.extractRenderState(g,mouseX,mouseY,delta);
    }
    private void label(GuiGraphicsExtractor g,String value,int x,int y,int max,int rgb) {
        g.text(font,font.plainSubstrByWidth(value,Math.max(0,max)),x,y,rgb);
    }
    private static void frame(GuiGraphicsExtractor g,int x,int y,int w,int h,int rgb) {
        g.fill(x,y,x+w,y+1,rgb); g.fill(x,y+h-1,x+w,y+h,rgb);
        g.fill(x,y,x+1,y+h,rgb); g.fill(x+w-1,y,x+w,y+h,rgb);
    }
    private void previewLine(GuiGraphicsExtractor g,int x0,int y0,int x1,int y1) {
        int steps=Math.max(Math.abs(x1-x0),Math.abs(y1-y0));
        for(int i=0;i<=steps;i++) {
            int x=steps==0?x0:x0+(x1-x0)*i/steps,y=steps==0?y0:y0+(y1-y0)*i/steps;
            g.fill(left+Math.max(0,x-brush/2)*scale,top+Math.max(0,y-brush/2)*scale,
                    left+Math.min(128,x-brush/2+brush)*scale,top+Math.min(128,y-brush/2+brush)*scale,palette.argb(color));
        }
    }
    private void select(int index,boolean updateHex) {
        if(index<4 || index>=248) return;
        color=index; recent.remove(index); recent.addFirst(index); while(recent.size()>8) recent.removeLast();
        if(updateHex) hex.setValue(palette.hex(index));
    }
    @Override public boolean mouseClicked(@NonNull MouseButtonEvent event,boolean doubleClick) {
        if(saveAttempt.frozen()) return super.mouseClicked(event,doubleClick);
        double mx=event.x(),my=event.y();
        if(event.button()==0 && colorMenu && mx>=panel && mx<panel+8*cell && my>=gridY && my<gridY+8*cell) {
            int at=page*64+(int)(mx-panel)/cell+8*((int)(my-gridY)/cell);
            if(at<palette.size()) select(palette.at(at),true); return true;
        }
        if(event.button()==0 && my>=height-49 && my<height-36 && mx>=left && mx<left+recent.size()*15) {
            select(new ArrayList<>(recent).get((int)(mx-left)/15),true); return true;
        }
        if(inside(mx,my)) {
            int x=(int)(mx-left)/scale,y=(int)(my-top)/scale;
            if(event.button()==1 || (event.button()==0 && tool==5)) { select(canvas.color(x,y),true); return true; }
            if(event.button()!=0) return false;
            hex.setFocused(false); canvas.beginStroke(); lastX=startX=x; lastY=startY=y;
            if(tool==2 || tool==3) canvas.fill(x,y,color,tolerance,colors,tool==3);
            else { drawing=true; if(tool!=4) canvas.line(x,y,x,y,brush,tool==1?0:color); }
            updateButtons(); return true;
        }
        return super.mouseClicked(event,doubleClick);
    }
    @Override public boolean mouseDragged(@NonNull MouseButtonEvent event,double dx,double dy) {
        if(drawing && event.button()==0 && !saveAttempt.frozen()) {
            if(!inside(event.x(),event.y())) { lastX=-1; return true; }
            int x=(int)(event.x()-left)/scale,y=(int)(event.y()-top)/scale;
            if(tool!=4) canvas.line(lastX<0?x:lastX,lastX<0?y:lastY,x,y,brush,tool==1?0:color);
            lastX=x; lastY=y; return true;
        }
        return super.mouseDragged(event,dx,dy);
    }
    @Override public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        if(event.button()==0 && drawing) {
            if(tool==4 && !saveAttempt.frozen() && inside(event.x(),event.y()))
                canvas.line(startX,startY,(int)(event.x()-left)/scale,(int)(event.y()-top)/scale,brush,color);
            drawing=false; updateButtons(); return true;
        }
        return super.mouseReleased(event);
    }
    @Override public boolean keyPressed(@NonNull KeyEvent event) {
        if(!saveAttempt.frozen() && !hex.isFocused() && (event.modifiers()&(GLFW.GLFW_MOD_CONTROL|GLFW.GLFW_MOD_SUPER))!=0) {
            if(event.key()==GLFW.GLFW_KEY_Z) {
                drawing=false; if((event.modifiers()&GLFW.GLFW_MOD_SHIFT)!=0) canvas.redo(); else canvas.undo(); updateButtons(); return true;
            }
            if(event.key()==GLFW.GLFW_KEY_Y) { drawing=false; canvas.redo(); updateButtons(); return true; }
        }
        return super.keyPressed(event);
    }
    private boolean inside(double x,double y) { return x>=left && y>=top && x<left+128*scale && y<top+128*scale; }
    @Override public void tick() {
        if(!saveAttempt.frozen() && ++ticks%100==0) ParadisepaintsClient.save(session,canvas.pixels(),false);
        if(saveAttempt.tick()) { status="timeout"; updateButtons(); }
    }
    @Override public void onClose() {
        if(!saveAttempt.waiting()) {
            drawing=false; status="saving";
            ParadisepaintsClient.save(session,saveAttempt.begin(canvas.pixels()),true); updateButtons();
        }
    }
    public void acknowledge(UUID id,boolean ok) {
        if(!session.equals(id)||!saveAttempt.frozen()) return;
        saveAttempt.rejected(); if(ok) minecraft.gui.setScreen(null); else { status="rejected"; updateButtons(); }
    }
    private void updateButtons() {
        boolean editable=!saveAttempt.frozen();
        toolsTab.active=editable && colorMenu; colorsTab.active=editable && !colorMenu;
        for(Button button:toolWidgets) { button.visible=!colorMenu && editable; button.active=editable; }
        for(int i=0;i<TOOLS.length;i++) toolWidgets.get(i).active=editable && i!=tool;
        undoButton.active=editable && canvas.canUndo(); redoButton.active=editable && canvas.canRedo();
        for(Button button:colorWidgets) { button.visible=colorMenu && editable; button.active=editable; }
        hex.setVisible(colorMenu && editable); hex.setEditable(editable);
        if(!colorMenu || !editable) hex.setFocused(false);
        saveButton.active=!saveAttempt.waiting(); saveButton.setMessage(text(saveAttempt.frozen()?"retry":"save"));
        closeButton.visible=saveAttempt.frozen() && !saveAttempt.waiting();
    }
    private static Component text(String key,Object... args) { return Component.translatable("paradisepaints.editor."+key,args); }
    @Override public boolean isPauseScreen() { return false; }
}
