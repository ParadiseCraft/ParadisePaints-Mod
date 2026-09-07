package me.andromedov.paradisepaints.client;

import java.io.IOException;
import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;

public final class PaintScreen extends Screen {
    private static final String[] TOOLS={"pencil","eraser","fill","replace","line","picker"};
    private static final int[] BRUSH_SIZES={1,2,3,4,8,10,16,32};
    private static final int[] TOLERANCES={0,15,30,45,60};
    private static final int[] VANILLA_RGB={
            0x1d1d21,0x474f52,0x9d9d97,0xf9fffe,0x835432,0xb02e26,0xf9801d,0xfed83d,
            0x5e7c16,0x80c71f,0x169c9c,0x3ab3da,0x3c44aa,0x8932b8,0xc74ebd,0xf38baa
    };
    private static final int TOOL_GAP=3;
    private static final int ACTIVE_BORDER=0xff69d2ff;

    private final UUID session;
    private final CanvasModel canvas;
    private final ColorPalette palette;
    private final int[] colors;
    private final int[] vanillaColors=new int[VANILLA_RGB.length];
    private final FavoriteColors favorites;
    private final ColorPickerModel customColor;
    private final String initialTitle;
    private final boolean pigments;
    private final int pigmentRed,pigmentGreen,pigmentBlue,pigmentCapacity;
    private final List<Button> toolWidgets=new ArrayList<>(),sizeWidgets=new ArrayList<>(),toleranceWidgets=new ArrayList<>();
    private final Deque<Integer> recent=new ArrayDeque<>();
    private final SaveAttempt saveAttempt=new SaveAttempt();
    private CanvasTexture canvasTexture;
    private long renderedRevision=-1;

    private int left,top,scale,panel,panelWidth;
    private int toolX,toolY,toolWidth,sizeY,sizeWidth,toleranceY,toleranceWidth;
    private int gridX,gridY,recentGridY,cell,titleY,paintY,favoriteY,favoriteWidth,favoriteHeight;
    private int pickerX,pickerY,pickerWidth,pickerHeight,hueX,hueWidth,hexY;
    private int color=34,requestedRgb,brush=1,tool,lastX,lastY,startX,startY,ticks,tolerance,pickerDrag;
    private long outboundSequence;
    private byte[] lastDraft;
    private int lastPickerX=Integer.MIN_VALUE,lastPickerY=Integer.MIN_VALUE;
    private boolean drawing,customMenu,syncingHex;
    private Boolean favoriteSelected;
    private String status="";
    private Button saveButton,closeButton,undoButton,redoButton,pickerButton,pickerCloseButton,favoriteButton;
    private EditBox hex,title;

    public PaintScreen(UUID session,String paintingTitle,byte[] pixels,int[] colors,boolean pigments,int red,int green,int blue,
            int capacity) {
        super(Component.literal("ParadisePaints")); this.session=session;
        canvas=new CanvasModel(pixels); palette=new ColorPalette(colors); this.colors=palette.colors();
        lastDraft=pixels.clone();
        for(int i=0;i<VANILLA_RGB.length;i++) vanillaColors[i]=palette.nearest(VANILLA_RGB[i]);
        favorites=FavoriteColors.openClient(); requestedRgb=palette.argb(color)&0xffffff;
        customColor=new ColorPickerModel(requestedRgb); remember(color);
        this.initialTitle=paintingTitle;
        this.pigments=pigments; pigmentRed=red; pigmentGreen=green; pigmentBlue=blue; pigmentCapacity=capacity;
    }

    @Override protected void init() {
        if(canvasTexture==null) canvasTexture=new CanvasTexture(minecraft,canvas.pixels(),colors);
        String currentTitle=title==null?initialTitle:title.getValue();
        drawing=false; pickerDrag=0; favoriteSelected=null;
        toolWidgets.clear(); sizeWidgets.clear(); toleranceWidgets.clear();
        panel=Math.max(142,width-216); panelWidth=Math.max(166,width-panel-8);
        scale=Math.max(1,Math.min((panel-22)/128,(height-104)/128));
        left=Math.max(8,(panel-128*scale)/2); top=Math.max(42,(height-70-128*scale)/2);

        int headerY=28;
        redoButton=addRenderableWidget(Button.builder(Component.literal("↷"),b->{ canvas.redo(); updateButtons(); }).tooltip(Tooltip.create(text("redo")))
                .bounds(panel+panelWidth-22,headerY,22,20).build());
        undoButton=addRenderableWidget(Button.builder(Component.literal("↶"),b->{ canvas.undo(); updateButtons(); }).tooltip(Tooltip.create(text("undo")))
                .bounds(panel+panelWidth-47,headerY,22,20).build());

        toolY=54;
        toolWidth=Math.max(20,(panelWidth-TOOL_GAP*(TOOLS.length-1))/TOOLS.length);
        int toolsTotal=toolWidth*TOOLS.length+TOOL_GAP*(TOOLS.length-1);
        toolX=panel+(panelWidth-toolsTotal)/2;
        for(int i=0;i<TOOLS.length;i++) {
            int selected=i;
            toolWidgets.add(addRenderableWidget(Button.builder(Component.empty(),b->{ tool=selected; updateButtons(); }).tooltip(Tooltip.create(text(TOOLS[i])))
                    .bounds(toolX+i*(toolWidth+TOOL_GAP),toolY,toolWidth,22).build()));
        }

        sizeY=98;
        int sizeGap=3;
        sizeWidth=(panelWidth-sizeGap*3)/4;
        for(int i=0;i<BRUSH_SIZES.length;i++) {
            int selected=BRUSH_SIZES[i];
            sizeWidgets.add(addRenderableWidget(Button.builder(Component.literal(Integer.toString(selected)),b->{
                brush=selected; updateButtons();
            }).bounds(panel+(i%4)*(sizeWidth+sizeGap),sizeY+(i/4)*21,sizeWidth,18).build()));
        }

        toleranceY=152;
        int toleranceGap=3;
        toleranceWidth=(panelWidth-toleranceGap*(TOLERANCES.length-1))/TOLERANCES.length;
        for(int i=0;i<TOLERANCES.length;i++) {
            int selected=TOLERANCES[i];
            toleranceWidgets.add(addRenderableWidget(Button.builder(Component.literal(Integer.toString(selected)),b->{
                tolerance=selected; updateButtons();
            }).bounds(panel+i*(toleranceWidth+toleranceGap),toleranceY,toleranceWidth,18).build()));
        }

        gridY=197;
        int verticalSpace=Math.max(32,height-gridY-112);
        cell=Math.max(8,Math.min(20,Math.min(panelWidth/8,verticalSpace/4)));
        gridX=panel+(panelWidth-cell*8)/2;
        recentGridY=gridY+cell*2+13;

        pickerButton=addRenderableWidget(Button.builder(Component.empty(),b->{
            customMenu=!customMenu; updateButtons();
        }).tooltip(Tooltip.create(text("custom"))).bounds(panel+panelWidth-22,170,22,18).build());
        pickerCloseButton=addRenderableWidget(Button.builder(Component.empty(),b->{
            customMenu=false; updateButtons();
        }).tooltip(Tooltip.create(text("presets"))).bounds(panel+panelWidth-22,170,22,18).build());

        pickerX=panel; pickerY=gridY;
        pickerWidth=Math.max(64,panelWidth-24); hueWidth=14; hueX=pickerX+pickerWidth+8;
        pickerHeight=Math.max(42,Math.min(72,height-pickerY-170));
        hexY=pickerY+pickerHeight+6;
        hex=addRenderableWidget(new EditBox(font,panel,hexY,panelWidth-25,18,text("hex")));
        hex.setMaxLength(7); setHex(requestedRgb);
        hex.setResponder(value->{
            if(syncingHex) return;
            var parsed=palette.parseRgb(value); hex.setTextColor(parsed.isPresent()?0xffffffff:0xffff7777);
            if(parsed.isPresent() && !saveAttempt.frozen()) chooseRgb(parsed.getAsInt(),false,true);
        });
        favoriteButton=addRenderableWidget(Button.builder(Component.empty(),b->toggleFavorite())
                .bounds(panel+panelWidth-22,hexY,22,18).build());
        favoriteY=hexY+24;
        favoriteWidth=panelWidth-25;
        favoriteHeight=12;

        int paletteBottom=recentGridY+cell*2;
        int customBottom=favoriteY+favoriteHeight;
        titleY=Math.max(paletteBottom,customBottom)+7;
        paintY=titleY+24;

        title=addRenderableWidget(new EditBox(font,panel,titleY,panelWidth,20,text("title")));
        title.setMaxLength(32); title.setValue(currentTitle); title.setResponder(value->updateButtons());
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
        renderTransparencyGrid(g);
        if(renderedRevision!=canvas.revision()) {
            canvasTexture.update(canvas.pixels(),colors); renderedRevision=canvas.revision();
        }
        canvasTexture.draw(g,left,top,128*scale,128*scale);
        if(!saveAttempt.frozen() && inside(mouseX,mouseY)) {
            int x=(mouseX-left)/scale,y=(mouseY-top)/scale;
            if(tool==4 && drawing) previewLine(g,startX,startY,x,y);
            else if(tool<2) {
                int x0=Math.max(0,x-brush/2),y0=Math.max(0,y-brush/2);
                int x1=Math.min(128,x-brush/2+brush),y1=Math.min(128,y-brush/2+brush);
                frame(g,left+x0*scale,top+y0*scale,(x1-x0)*scale,(y1-y0)*scale,0xffffffff);
            }
        }

        if(!saveAttempt.frozen()) {
            label(g,text("tools").getString(),panel,34,panelWidth-54,0xffd5dbe3);
            label(g,text("size").getString(),panel,84,panelWidth,0xffd5dbe3);
            label(g,text("tolerance",tolerance).getString(),panel,138,panelWidth,0xff8996a7);
            label(g,text("colors").getString(),panel,174,panelWidth-27,0xffd5dbe3);
            if(customMenu) {
                renderCustomPicker(g);
                renderFavorites(g);
            } else {
                renderPalette(g);
            }
            if(pigments) renderPaintStock(g);
        } else {
            int y=58;
            for(var line:font.split(text(status),panelWidth)) { g.text(font,line,panel,y,0xffc9d5e4); y+=11; }
            if(!saveAttempt.waiting()) {
                y+=12;
                for(var line:font.split(text("close-warning"),panelWidth)) { g.text(font,line,panel,y,0xffffcc66); y+=11; }
            }
        }

        label(g,status.isEmpty()?text("hint").getString():text(status).getString(),left,height-28,panel-left-12,0xffc9d5e4);
        if(saveAttempt.frozen() && !saveAttempt.waiting()) label(g,text("close-warning").getString(),left,height-16,panel-left-12,0xffffcc66);
        super.extractRenderState(g,mouseX,mouseY,delta);
        if(!saveAttempt.frozen()) renderButtonIcons(g);
    }

    private void renderPalette(GuiGraphicsExtractor g) {
        List<Integer> recentColors=new ArrayList<>(recent);
        label(g,text("default-colors").getString(),gridX,gridY-10,cell*8,0xff8996a7);
        label(g,text("recent-colors").getString(),gridX,recentGridY-10,cell*8,0xff8996a7);
        for(int i=0;i<16;i++) {
            int x=gridX+(i%8)*cell,y=gridY+(i/8)*cell,index=vanillaColors[i];
            g.fill(x+1,y+1,x+cell-1,y+cell-1,palette.argb(index));
            if(index==color) frame(g,x,y,cell,cell,0xffffffff);
            int recentX=gridX+(i%8)*cell,recentY=recentGridY+(i/8)*cell;
            g.fill(recentX+1,recentY+1,recentX+cell-1,recentY+cell-1,0xff1b2028);
            if(i<recentColors.size()) {
                int recentIndex=recentColors.get(i);
                g.fill(recentX+1,recentY+1,recentX+cell-1,recentY+cell-1,palette.argb(recentIndex));
                if(recentIndex==color) frame(g,recentX,recentY,cell,cell,0xffffffff);
            }
        }
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
    }

    private void renderFavorites(GuiGraphicsExtractor g) {
        List<Integer> values=favorites.values();
        label(g,text("favorites").getString(),panel,favoriteY-10,panelWidth,0xff8996a7);
        if(values.isEmpty()) {
            label(g,text("favorite-empty").getString(),panel,favoriteY+1,panelWidth,0xff8996a7);
            return;
        }
        for(int i=0;i<values.size();i++) {
            int rgb=values.get(i),x=panel+i*favoriteWidth/16,nextX=panel+(i+1)*favoriteWidth/16;
            g.fill(x+1,favoriteY+1,nextX-1,favoriteY+favoriteHeight-1,0xff000000|rgb);
            if(rgb==requestedRgb) frame(g,x,favoriteY,nextX-x,favoriteHeight,0xffffffff);
        }
    }

    private void renderPaintStock(GuiGraphicsExtractor g) {
        label(g,text("pigment-stock-title").getString(),panel,paintY,panelWidth,0xff8996a7);
        int column=panelWidth/3;
        renderPaintChannel(g,panel,paintY+11,column,"R",pigmentRed,0xffff6b6b);
        renderPaintChannel(g,panel+column,paintY+11,column,"G",pigmentGreen,0xff72e48b);
        renderPaintChannel(g,panel+column*2,paintY+11,panelWidth-column*2,"B",pigmentBlue,0xff69a8ff);
    }

    private void renderPaintChannel(GuiGraphicsExtractor g,int x,int y,int width,String channel,int amount,int rgb) {
        g.fill(x,y,x+2,y+9,rgb);
        label(g,channel+" "+formatPercent(amount,pigmentCapacity),x+5,y,width-5,0xffd5dbe3);
    }

    private void renderButtonIcons(GuiGraphicsExtractor g) {
        for(int i=0;i<TOOLS.length;i++) {
            int x=toolX+i*(toolWidth+TOOL_GAP),colorValue=toolWidgets.get(i).active?0xffe8edf4:0xff78818d;
            renderToolIcon(g,i,x,toolY,toolWidth,22,colorValue);
            if(i==tool) frame(g,x,toolY,toolWidth,22,ACTIVE_BORDER);
        }
        for(int i=0;i<sizeWidgets.size();i++) if(BRUSH_SIZES[i]==brush) {
            int x=panel+(i%4)*(sizeWidth+3),y=sizeY+(i/4)*21;
            frame(g,x,y,sizeWidth,18,ACTIVE_BORDER);
        }
        for(int i=0;i<toleranceWidgets.size();i++) if(TOLERANCES[i]==tolerance) {
            int x=panel+i*(toleranceWidth+3);
            frame(g,x,toleranceY,toleranceWidth,18,ACTIVE_BORDER);
        }
        int buttonX=panel+panelWidth-22,buttonY=170;
        g.fill(buttonX+4,buttonY+4,buttonX+18,buttonY+14,0xff287fd1);
        if(customMenu) {
            g.fill(buttonX+7,buttonY+8,buttonX+15,buttonY+9,0xffffffff);
        } else {
            g.fill(buttonX+7,buttonY+9,buttonX+15,buttonY+10,0xffffffff);
            g.fill(buttonX+10,buttonY+6,buttonX+11,buttonY+13,0xffffffff);
        }
        if(customMenu) frame(g,buttonX,buttonY,22,18,ACTIVE_BORDER);
        if(customMenu) renderFavoriteIcon(g,panel+panelWidth-22,hexY);
    }

    private void renderToolIcon(GuiGraphicsExtractor g,int icon,int x,int y,int width,int height,int rgb) {
        int cx=x+width/2,cy=y+height/2;
        switch(icon) {
            case 0 -> {
                for(int i=-5;i<=4;i++) g.fill(cx+i,cy-i-1,cx+i+2,cy-i+1,rgb);
                g.fill(cx-6,cy+5,cx-3,cy+6,0xffd7b56d);
            }
            case 1 -> {
                frame(g,cx-6,cy-4,12,8,rgb);
                g.fill(cx-4,cy-2,cx+4,cy+2,0xffdc8b9a);
            }
            case 2 -> {
                frame(g,cx-6,cy-5,10,9,rgb);
                g.fill(cx-3,cy-7,cx+5,cy-5,rgb);
                g.fill(cx+6,cy+3,cx+8,cy+6,0xff62b8ff);
            }
            case 3 -> {
                g.fill(cx-6,cy-4,cx+5,cy-3,rgb); g.fill(cx+3,cy-6,cx+7,cy-1,rgb);
                g.fill(cx-5,cy+4,cx+6,cy+5,rgb); g.fill(cx-7,cy+1,cx-3,cy+7,rgb);
            }
            case 4 -> {
                for(int i=-6;i<=6;i++) g.fill(cx+i,cy-i/2,cx+i+1,cy-i/2+1,rgb);
            }
            default -> {
                g.fill(cx-6,cy,cx+7,cy+1,rgb); g.fill(cx,cy-6,cx+1,cy+7,rgb);
                frame(g,cx-3,cy-3,7,7,rgb);
            }
        }
    }

    private void renderFavoriteIcon(GuiGraphicsExtractor g,int x,int y) {
        int rgb=Boolean.TRUE.equals(favoriteSelected)?0xffffd35c:0xffe8edf4,cx=x+11,cy=y+9;
        g.fill(cx-5,cy,cx+6,cy+1,rgb); g.fill(cx,cy-5,cx+1,cy+6,rgb);
        if(Boolean.TRUE.equals(favoriteSelected)) {
            g.fill(cx-3,cy-3,cx+4,cy+4,0x33252c36);
            g.fill(cx-5,cy,cx+6,cy+1,rgb);
        }
    }

    private void label(GuiGraphicsExtractor g,String value,int x,int y,int max,int rgb) {
        g.text(font,font.plainSubstrByWidth(value,Math.max(0,max)),x,y,rgb);
    }

    private void renderTransparencyGrid(GuiGraphicsExtractor g) {
        int square=8*scale;
        for(int y=0;y<16;y++) for(int x=0;x<16;x++) {
            int rgb=((x+y)&1)==0?0xffeeeeee:0xffaeb4bc;
            g.fill(left+x*square,top+y*square,left+(x+1)*square,top+(y+1)*square,rgb);
        }
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
        chooseRgb(rgb,updateHex,updatePicker,true);
    }

    private void chooseRgb(int rgb,boolean updateHex,boolean updatePicker,boolean addToRecent) {
        requestedRgb=rgb&0xffffff;
        color=palette.nearest(requestedRgb);
        if(addToRecent) remember(color);
        if(updatePicker) customColor.setRgb(requestedRgb);
        if(updateHex) setHex(requestedRgb);
        refreshFavoriteButton();
    }

    private void remember(int index) {
        recent.remove(index); recent.addFirst(index);
        while(recent.size()>16) recent.removeLast();
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
        if(favoriteButton!=null && !Objects.equals(favoriteSelected,selected)) {
            favoriteSelected=selected;
            favoriteButton.setTooltip(Tooltip.create(text(selected?"remove-favorite":"add-favorite")));
        }
    }

    private void updateCustomFromPointer(double x,double y,int area,boolean updateHex) {
        int sampleX=(int)x,sampleY=(int)y;
        if(sampleX==lastPickerX && sampleY==lastPickerY) return;
        lastPickerX=sampleX; lastPickerY=sampleY;
        if(area==1) customColor.setSaturationValue((float)((x-pickerX)/Math.max(1,pickerWidth-1)),
                1-(float)((y-pickerY)/Math.max(1,pickerHeight-1)));
        else customColor.setHue((float)((y-pickerY)/Math.max(1,pickerHeight-1)));
        chooseRgb(customColor.rgb(),updateHex,false,false);
    }

    @Override public boolean mouseClicked(@NonNull MouseButtonEvent event,boolean doubleClick) {
        if(saveAttempt.frozen()) return super.mouseClicked(event,doubleClick);
        double mx=event.x(),my=event.y();
        if(event.button()==0 && customMenu) {
            if(mx>=pickerX && mx<pickerX+pickerWidth && my>=pickerY && my<pickerY+pickerHeight) {
                pickerDrag=1; lastPickerX=lastPickerY=Integer.MIN_VALUE; updateCustomFromPointer(mx,my,1,true); return true;
            }
            if(mx>=hueX && mx<hueX+hueWidth && my>=pickerY && my<pickerY+pickerHeight) {
                pickerDrag=2; lastPickerX=lastPickerY=Integer.MIN_VALUE; updateCustomFromPointer(mx,my,2,true); return true;
            }
            List<Integer> values=favorites.values();
            if(my>=favoriteY && my<favoriteY+favoriteHeight && mx>=panel && mx<panel+favoriteWidth) {
                int at=(int)((mx-panel)*16/favoriteWidth);
                if(at<values.size()) chooseRgb(values.get(at),true,true);
                return true;
            }
        }
        if(event.button()==0 && !customMenu && mx>=gridX && mx<gridX+8*cell) {
            if(my>=gridY && my<gridY+2*cell) {
                int at=(int)(mx-gridX)/cell+8*((int)(my-gridY)/cell);
                chooseMap(vanillaColors[at]); return true;
            }
            if(my>=recentGridY && my<recentGridY+2*cell) {
                int at=(int)(mx-gridX)/cell+8*((int)(my-recentGridY)/cell);
                List<Integer> values=new ArrayList<>(recent);
                if(at<values.size()) chooseMap(values.get(at));
                return true;
            }
        }
        if(inside(mx,my)) {
            int x=(int)(mx-left)/scale,y=(int)(my-top)/scale;
            if(event.button()==1 || (event.button()==0 && tool==5)) { chooseMap(canvas.color(x,y)); return true; }
            if(event.button()!=0) return false;
            hex.setFocused(false); title.setFocused(false); canvas.beginStroke(); lastX=startX=x; lastY=startY=y;
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
            pickerDrag=0; lastPickerX=lastPickerY=Integer.MIN_VALUE; remember(color); setHex(requestedRgb); return true;
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
        if(!saveAttempt.frozen() && ++ticks%100==0) {
            byte[] pixels=canvas.pixels();
            if(lastDraft==null || !java.util.Arrays.equals(lastDraft,pixels)) {
                lastDraft=pixels.clone();
                ParadisepaintsClient.save(session,++outboundSequence,title.getValue(),pixels,false);
            }
        }
        if(saveAttempt.tick()) { status="timeout"; updateButtons(); }
    }

    @Override public void onClose() {
        if(!saveAttempt.waiting()) {
            if(title.getValue().trim().isEmpty()) { status="title-required"; return; }
            drawing=false; pickerDrag=0; status="saving";
            ParadisepaintsClient.save(session,++outboundSequence,title.getValue().trim(),saveAttempt.begin(canvas.pixels()),true); updateButtons();
        }
    }

    public void acknowledge(UUID id,int result) {
        if(!session.equals(id)||!saveAttempt.frozen()) return;
        saveAttempt.rejected(); if(result==1) minecraft.gui.setScreen(null);
        else { status=result==2?"pigment-required":"rejected"; updateButtons(); }
    }

    public void closeFromServer(UUID id) {
        if(session.equals(id)) minecraft.gui.setScreen(null);
    }

    private void updateButtons() {
        boolean editable=!saveAttempt.frozen();
        for(Button button:toolWidgets) { button.visible=editable; button.active=editable; }
        for(Button button:sizeWidgets) { button.visible=editable; button.active=editable; }
        for(Button button:toleranceWidgets) { button.visible=editable; button.active=editable; }
        undoButton.visible=editable; redoButton.visible=editable;
        undoButton.active=editable && canvas.canUndo(); redoButton.active=editable && canvas.canRedo();
        pickerButton.visible=editable && !customMenu; pickerButton.active=editable;
        pickerCloseButton.visible=editable && customMenu; pickerCloseButton.active=editable;
        hex.setVisible(editable && customMenu); hex.setEditable(editable && customMenu);
        favoriteButton.visible=editable && customMenu; favoriteButton.active=editable;
        title.setVisible(editable); title.setEditable(editable);
        if(!customMenu || !editable) hex.setFocused(false);
        if(!editable) title.setFocused(false);
        saveButton.active=!saveAttempt.waiting() && !title.getValue().trim().isEmpty();
        saveButton.setMessage(text(saveAttempt.frozen()?"retry":"save"));
        closeButton.visible=saveAttempt.frozen() && !saveAttempt.waiting();
        refreshFavoriteButton();
    }

    private static String formatPercent(int amount,int capacity) {
        return Math.min(100,((long)Math.max(0,amount)*100+capacity/2L)/capacity)+"%";
    }

    private static Component text(String key,Object... args) { return Component.translatable("paradisepaints.editor."+key,args); }
    @Override public void removed() { if(canvasTexture!=null) { canvasTexture.close(); canvasTexture=null; } }
    @Override public boolean isPauseScreen() { return false; }
}
