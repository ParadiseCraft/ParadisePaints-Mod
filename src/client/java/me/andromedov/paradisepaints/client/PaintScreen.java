package me.andromedov.paradisepaints.client;

import java.io.IOException;
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
    private final FavoriteColors favorites;
    private final ColorPickerModel customColor;
    private final String initialTitle;
    private final boolean pigments;
    private final int pigmentRed,pigmentGreen,pigmentBlue;
    private final List<Button> toolWidgets=new ArrayList<>(),pageWidgets=new ArrayList<>();
    private final Deque<Integer> recent=new ArrayDeque<>();
    private final SaveAttempt saveAttempt=new SaveAttempt();
    private int left,top,scale,panel,panelWidth,cell,gridX,gridY,below,favoriteY;
    private int pickerX,pickerY,pickerWidth,pickerHeight,hueX,hueWidth;
    private int color=34,requestedRgb,brush=1,tool,lastX,lastY,startX,startY,ticks,page,tolerance,pickerDrag;
    private int lastPickerX=Integer.MIN_VALUE,lastPickerY=Integer.MIN_VALUE;
    private boolean drawing,colorMenu,customMenu,syncingHex;
    private Boolean favoriteSelected;
    private String status="";
    private Button saveButton,closeButton,undoButton,redoButton,toolsTab,colorsTab,presetsTab,customTab,favoriteButton;
    private EditBox hex,title;

    public PaintScreen(UUID session,String paintingTitle,byte[] pixels,int[] colors,boolean pigments,int red,int green,int blue) {
        super(Component.literal("ParadisePaints")); this.session=session;
        canvas=new CanvasModel(pixels); palette=new ColorPalette(colors); this.colors=palette.colors();
        favorites=FavoriteColors.openClient(); requestedRgb=palette.argb(color)&0xffffff;
        customColor=new ColorPickerModel(requestedRgb); recent.add(color);
        this.initialTitle=paintingTitle;
        this.pigments=pigments; pigmentRed=red; pigmentGreen=green; pigmentBlue=blue;
    }
    @Override protected void init() {
        drawing=false; pickerDrag=0; favoriteSelected=null; toolWidgets.clear(); pageWidgets.clear();
        panel=Math.max(134,width-202); panelWidth=Math.max(150,width-panel-8);
        scale=Math.max(1,Math.min((panel-22)/128,(height-104)/128));
        left=Math.max(8,(panel-128*scale)/2); top=Math.max(42,(height-70-128*scale)/2);
        int gap=8,half=(panelWidth-gap)/2;
        toolsTab=addRenderableWidget(Button.builder(text("tools"),b->{ colorMenu=false; updateButtons(); })
                .bounds(panel,28,half,20).build());
        colorsTab=addRenderableWidget(Button.builder(text("colors"),b->{ colorMenu=true; updateButtons(); })
                .bounds(panel+half+gap,28,half,20).build());
        for(int i=0;i<TOOLS.length;i++) {
            int selected=i;
            toolWidgets.add(addRenderableWidget(Button.builder(text(TOOLS[i]),b->{ tool=selected; updateButtons(); })
                    .bounds(panel+(i%2)*(half+gap),58+(i/2)*28,half,22).build()));
        }
        toolWidgets.add(addRenderableWidget(Button.builder(text("brush",brush),b->{
            brush=brush==16?1:brush*2; b.setMessage(text("brush",brush));
        }).bounds(panel,146,panelWidth,22).build()));
        toolWidgets.add(addRenderableWidget(Button.builder(text("tolerance",tolerance),b->{
            tolerance=tolerance==60?0:tolerance+15; b.setMessage(text("tolerance",tolerance));
        }).bounds(panel,174,panelWidth,22).build()));
        undoButton=addRenderableWidget(Button.builder(text("undo"),b->{ canvas.undo(); updateButtons(); })
                .bounds(panel,202,half,22).build());
        redoButton=addRenderableWidget(Button.builder(text("redo"),b->{ canvas.redo(); updateButtons(); })
                .bounds(panel+half+gap,202,half,22).build());
        toolWidgets.add(undoButton); toolWidgets.add(redoButton);

        presetsTab=addRenderableWidget(Button.builder(text("presets"),b->{ customMenu=false; updateButtons(); })
                .bounds(panel,58,half,20).build());
        customTab=addRenderableWidget(Button.builder(text("custom"),b->{ customMenu=true; updateButtons(); })
                .bounds(panel+half+gap,58,half,20).build());
        gridY=86;
        int gridSize=Math.min(panelWidth-18,Math.max(64,height-gridY-122));
        cell=Math.max(8,gridSize/8); gridSize=cell*8; gridX=panel+(panelWidth-gridSize)/2;
        below=gridY+gridSize+6;
        pickerX=gridX; pickerY=gridY; pickerHeight=gridSize; pickerWidth=Math.max(40,gridSize-24);
        hueWidth=14; hueX=pickerX+pickerWidth+8;
        pageWidgets.add(addRenderableWidget(Button.builder(Component.literal("<"),b->{page=(page+3)%4;})
                .bounds(gridX,below,32,18).build()));
        pageWidgets.add(addRenderableWidget(Button.builder(Component.literal(">"),b->{page=(page+1)%4;})
                .bounds(gridX+gridSize-32,below,32,18).build()));
        hex=addRenderableWidget(new EditBox(font,panel,below+24,panelWidth,18,text("hex")));
        hex.setMaxLength(7); setHex(requestedRgb);
        hex.setResponder(value->{
            if(syncingHex) return;
            var parsed=palette.parseRgb(value); hex.setTextColor(parsed.isPresent()?0xffffffff:0xffff7777);
            if(parsed.isPresent() && !saveAttempt.frozen()) chooseRgb(parsed.getAsInt(),false,true);
        });
        favoriteButton=addRenderableWidget(Button.builder(text("add-favorite"),b->toggleFavorite())
                .bounds(panel,below+48,panelWidth,18).build());
        favoriteY=below+72;
        title=addRenderableWidget(new EditBox(font,panel,234,panelWidth,20,text("title")));
        title.setMaxLength(32); title.setValue(initialTitle); title.setResponder(value->updateButtons());
        saveButton=addRenderableWidget(Button.builder(text("save"),b->onClose()).bounds(panel,height-26,panelWidth,20).build());
        closeButton=addRenderableWidget(Button.builder(text("close"),b->{
            if(saveAttempt.frozen() && !saveAttempt.waiting()) minecraft.gui.setScreen(null);
        }).bounds(panel,height-50,panelWidth,20).build());
        updateButtons();
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta) {
        g.fill(0,0,width,height,0xff171b22); g.fill(panel-8,20,width-4,height-4,0xff252c36);
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
            if(customMenu) renderCustomPicker(g); else renderPresets(g);
            renderFavorites(g);
        } else if(!saveAttempt.frozen() && pigments) {
            label(g,text("pigment-stock",pigmentRed,pigmentGreen,pigmentBlue).getString(),panel,264,panelWidth,0xffd5dbe3);
            for(var line:font.split(text("pigment-help"),panelWidth)) {
                g.text(font,line,panel,282,0xff8996a7);
                break;
            }
        }
        if(saveAttempt.frozen()) {
            int y=58;
            for(var line:font.split(text(status),panelWidth)) { g.text(font,line,panel,y,0xffc9d5e4); y+=11; }
            if(!saveAttempt.waiting()) {
                y+=12;
                for(var line:font.split(text("close-warning"),panelWidth)) { g.text(font,line,panel,y,0xffffcc66); y+=11; }
            }
        }
        int recentY=height-49,i=0;
        for(int index:recent) {
            int x=left+i++*18; g.fill(x,recentY,x+15,recentY+15,palette.argb(index));
            if(index==color) frame(g,x-1,recentY-1,17,17,0xffffffff);
        }
        label(g,status.isEmpty()?text("hint").getString():text(status).getString(),left,height-28,panel-left-12,0xffc9d5e4);
        if(saveAttempt.frozen() && !saveAttempt.waiting()) label(g,text("close-warning").getString(),left,height-16,panel-left-12,0xffffcc66);
        super.extractRenderState(g,mouseX,mouseY,delta);
    }
    private void renderPresets(GuiGraphicsExtractor g) {
        for(int i=0;i<64 && page*64+i<palette.size();i++) {
            int index=palette.at(page*64+i),x=gridX+(i%8)*cell,y=gridY+(i/8)*cell;
            g.fill(x+2,y+2,x+cell-2,y+cell-2,palette.argb(index));
            if(index==color) frame(g,x,y,cell,cell,0xffffffff);
        }
        label(g,(page+1)+" / 4",gridX+40,below+5,cell*8-80,0xffc9d5e4);
    }
    private void renderCustomPicker(GuiGraphicsExtractor g) {
        int sample=4;
        for(int y=0;y<pickerHeight;y+=sample) for(int x=0;x<pickerWidth;x+=sample) {
            int rgb=ColorPickerModel.rgb(customColor.hue(),x/(float)Math.max(1,pickerWidth-1),1-y/(float)Math.max(1,pickerHeight-1));
            g.fill(pickerX+x,pickerY+y,pickerX+Math.min(pickerWidth,x+sample),pickerY+Math.min(pickerHeight,y+sample),0xff000000|rgb);
        }
        for(int y=0;y<pickerHeight;y+=sample) {
            int rgb=ColorPickerModel.rgb(y/(float)Math.max(1,pickerHeight-1),1,1);
            g.fill(hueX,pickerY+y,hueX+hueWidth,pickerY+Math.min(pickerHeight,y+sample),0xff000000|rgb);
        }
        int markerX=pickerX+Math.round(customColor.saturation()*(pickerWidth-1));
        int markerY=pickerY+Math.round((1-customColor.value())*(pickerHeight-1));
        frame(g,markerX-3,markerY-3,7,7,0xffffffff);
        int hueY=pickerY+Math.round(customColor.hue()*(pickerHeight-1));
        frame(g,hueX-2,hueY-2,hueWidth+4,5,0xffffffff);
        label(g,text("matched",palette.hex(color)).getString(),gridX,below+5,cell*8,0xffc9d5e4);
    }
    private void renderFavorites(GuiGraphicsExtractor g) {
        List<Integer> values=favorites.values();
        if(values.isEmpty()) {
            label(g,text("favorite-empty").getString(),panel,favoriteY+3,panelWidth,0xff8996a7);
            return;
        }
        for(int i=0;i<values.size();i++) {
            int rgb=values.get(i),x=panel+i*18;
            g.fill(x,favoriteY,x+15,favoriteY+15,0xff000000|rgb);
            if(rgb==requestedRgb) frame(g,x-1,favoriteY-1,17,17,0xffffffff);
        }
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
    private void chooseMap(int index) {
        if(index<4 || index>=248) return;
        chooseRgb(palette.argb(index)&0xffffff,true,true);
    }
    private void chooseRgb(int rgb,boolean updateHex,boolean updatePicker) {
        requestedRgb=rgb&0xffffff;
        int selected=palette.nearest(requestedRgb);
        if(selected!=color) {
            color=selected; recent.remove(color); recent.addFirst(color); while(recent.size()>8) recent.removeLast();
        }
        if(updatePicker) customColor.setRgb(requestedRgb);
        if(updateHex) setHex(requestedRgb);
        refreshFavoriteButton();
    }
    private void setHex(int rgb) {
        if(hex==null) return;
        syncingHex=true;
        try { hex.setValue(String.format(Locale.ROOT,"#%06X",rgb&0xffffff)); hex.setTextColor(0xffffffff); }
        finally { syncingHex=false; }
    }
    private void toggleFavorite() {
        try { favorites.toggle(requestedRgb); status=""; refreshFavoriteButton(); }
        catch(IOException ignored) { status="favorite-error"; }
    }
    private void refreshFavoriteButton() {
        boolean selected=favorites.contains(requestedRgb);
        if(favoriteButton!=null && !java.util.Objects.equals(favoriteSelected,selected)) {
            favoriteSelected=selected;
            favoriteButton.setMessage(text(selected?"remove-favorite":"add-favorite"));
        }
    }
    private void updateCustomFromPointer(double x,double y,int area,boolean updateHex) {
        int sampleX=(int)x,sampleY=(int)y;
        if(sampleX==lastPickerX && sampleY==lastPickerY) return;
        lastPickerX=sampleX; lastPickerY=sampleY;
        if(area==1) customColor.setSaturationValue((float)((x-pickerX)/Math.max(1,pickerWidth-1)),
                1-(float)((y-pickerY)/Math.max(1,pickerHeight-1)));
        else customColor.setHue((float)((y-pickerY)/Math.max(1,pickerHeight-1)));
        chooseRgb(customColor.rgb(),updateHex,false);
    }
    @Override public boolean mouseClicked(@NonNull MouseButtonEvent event,boolean doubleClick) {
        if(saveAttempt.frozen()) return super.mouseClicked(event,doubleClick);
        double mx=event.x(),my=event.y();
        if(event.button()==0 && colorMenu && customMenu) {
            if(mx>=pickerX && mx<pickerX+pickerWidth && my>=pickerY && my<pickerY+pickerHeight) {
                pickerDrag=1; lastPickerX=lastPickerY=Integer.MIN_VALUE; updateCustomFromPointer(mx,my,1,true); return true;
            }
            if(mx>=hueX && mx<hueX+hueWidth && my>=pickerY && my<pickerY+pickerHeight) {
                pickerDrag=2; lastPickerX=lastPickerY=Integer.MIN_VALUE; updateCustomFromPointer(mx,my,2,true); return true;
            }
        }
        if(event.button()==0 && colorMenu && !customMenu && mx>=gridX && mx<gridX+8*cell && my>=gridY && my<gridY+8*cell) {
            int at=page*64+(int)(mx-gridX)/cell+8*((int)(my-gridY)/cell);
            if(at<palette.size()) chooseMap(palette.at(at)); return true;
        }
        List<Integer> favoriteValues=favorites.values();
        if(event.button()==0 && colorMenu && my>=favoriteY && my<favoriteY+15 && mx>=panel && mx<panel+favoriteValues.size()*18) {
            chooseRgb(favoriteValues.get((int)(mx-panel)/18),true,true); return true;
        }
        if(event.button()==0 && my>=height-49 && my<height-34 && mx>=left && mx<left+recent.size()*18) {
            chooseMap(new ArrayList<>(recent).get((int)(mx-left)/18)); return true;
        }
        if(inside(mx,my)) {
            int x=(int)(mx-left)/scale,y=(int)(my-top)/scale;
            if(event.button()==1 || (event.button()==0 && tool==5)) { chooseMap(canvas.color(x,y)); return true; }
            if(event.button()!=0) return false;
            hex.setFocused(false); canvas.beginStroke(); lastX=startX=x; lastY=startY=y;
            if(tool==2 || tool==3) canvas.fill(x,y,color,tolerance,colors,tool==3);
            else { drawing=true; if(tool!=4) canvas.line(x,y,x,y,brush,tool==1?0:color); }
            updateButtons(); return true;
        }
        return super.mouseClicked(event,doubleClick);
    }
    @Override public boolean mouseDragged(@NonNull MouseButtonEvent event,double dx,double dy) {
        if(pickerDrag!=0 && event.button()==0 && !saveAttempt.frozen()) {
            double x=Math.max(pickerX,Math.min(pickerX+pickerWidth-1,event.x()));
            double y=Math.max(pickerY,Math.min(pickerY+pickerHeight-1,event.y()));
            updateCustomFromPointer(x,y,pickerDrag,false); return true;
        }
        if(drawing && event.button()==0 && !saveAttempt.frozen()) {
            if(!inside(event.x(),event.y())) { lastX=-1; return true; }
            int x=(int)(event.x()-left)/scale,y=(int)(event.y()-top)/scale;
            if(tool!=4) canvas.line(lastX<0?x:lastX,lastX<0?y:lastY,x,y,brush,tool==1?0:color);
            lastX=x; lastY=y; return true;
        }
        return super.mouseDragged(event,dx,dy);
    }
    @Override public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        if(event.button()==0 && pickerDrag!=0) {
            pickerDrag=0; lastPickerX=lastPickerY=Integer.MIN_VALUE; setHex(requestedRgb); return true;
        }
        if(event.button()==0 && drawing) {
            if(tool==4 && !saveAttempt.frozen() && inside(event.x(),event.y()))
                canvas.line(startX,startY,(int)(event.x()-left)/scale,(int)(event.y()-top)/scale,brush,color);
            drawing=false; updateButtons(); return true;
        }
        return super.mouseReleased(event);
    }
    @Override public boolean keyPressed(@NonNull KeyEvent event) {
        if(!saveAttempt.frozen() && !hex.isFocused() && !title.isFocused() && (event.modifiers()&(GLFW.GLFW_MOD_CONTROL|GLFW.GLFW_MOD_SUPER))!=0) {
            if(event.key()==GLFW.GLFW_KEY_Z) {
                drawing=false; if((event.modifiers()&GLFW.GLFW_MOD_SHIFT)!=0) canvas.redo(); else canvas.undo(); updateButtons(); return true;
            }
            if(event.key()==GLFW.GLFW_KEY_Y) { drawing=false; canvas.redo(); updateButtons(); return true; }
        }
        return super.keyPressed(event);
    }
    private boolean inside(double x,double y) { return x>=left && y>=top && x<left+128*scale && y<top+128*scale; }
    @Override public void tick() {
        if(!saveAttempt.frozen() && ++ticks%100==0) ParadisepaintsClient.save(session,title.getValue(),canvas.pixels(),false);
        if(saveAttempt.tick()) { status="timeout"; updateButtons(); }
    }
    @Override public void onClose() {
        if(!saveAttempt.waiting()) {
            if(title.getValue().trim().isEmpty()) { status="title-required"; return; }
            drawing=false; pickerDrag=0; status="saving";
            ParadisepaintsClient.save(session,title.getValue().trim(),saveAttempt.begin(canvas.pixels()),true); updateButtons();
        }
    }
    public void acknowledge(UUID id,int result) {
        if(!session.equals(id)||!saveAttempt.frozen()) return;
        saveAttempt.rejected(); if(result==1) minecraft.gui.setScreen(null);
        else { status=result==2?"pigment-required":"rejected"; updateButtons(); }
    }
    private void updateButtons() {
        boolean editable=!saveAttempt.frozen();
        toolsTab.active=editable && colorMenu; colorsTab.active=editable && !colorMenu;
        for(Button button:toolWidgets) { button.visible=!colorMenu && editable; button.active=editable; }
        for(int i=0;i<TOOLS.length;i++) toolWidgets.get(i).active=editable && i!=tool;
        undoButton.active=editable && canvas.canUndo(); redoButton.active=editable && canvas.canRedo();
        presetsTab.visible=colorMenu && editable; customTab.visible=colorMenu && editable;
        presetsTab.active=editable && customMenu; customTab.active=editable && !customMenu;
        for(Button button:pageWidgets) { button.visible=colorMenu && !customMenu && editable; button.active=editable; }
        hex.setVisible(colorMenu && editable); hex.setEditable(editable);
        favoriteButton.visible=colorMenu && editable; favoriteButton.active=editable;
        title.setVisible(!colorMenu && editable); title.setEditable(editable);
        if(!colorMenu || !editable) hex.setFocused(false);
        saveButton.active=!saveAttempt.waiting() && !title.getValue().trim().isEmpty(); saveButton.setMessage(text(saveAttempt.frozen()?"retry":"save"));
        closeButton.visible=saveAttempt.frozen() && !saveAttempt.waiting(); refreshFavoriteButton();
    }
    private static Component text(String key,Object... args) { return Component.translatable("paradisepaints.editor."+key,args); }
    @Override public boolean isPauseScreen() { return false; }
}
