package me.andromedov.paradisepaints.client;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

/** Server-fed moderation view. There is deliberately no client packet that can request or authorize this data. */
public final class GalleryScreen extends Screen {
    private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());
    private final UUID request;
    private final int page,pages,total,expected;
    private final int[] palette;
    private final Map<Integer,GalleryEntry> entries=new LinkedHashMap<>();
    private Button previous,next,confirm,cancel;
    private EditBox reason;
    private int selectedMap=-1;

    public GalleryScreen(UUID request,int page,int pages,int total,int expected,int[] palette) {
        super(Component.translatable("paradisepaints.gallery.title"));
        this.request=request; this.page=page; this.pages=pages; this.total=total; this.expected=expected;
        this.palette=palette.clone();
    }
    public UUID request() { return request; }
    public void add(GalleryEntry entry) { if(request.equals(entry.request())) entries.put(entry.mapId(),entry); }
    @Override protected void init() {
        int center=width/2;
        previous=addRenderableWidget(Button.builder(text("previous"),b->request(page-1)).bounds(center-104,height-25,98,20).build());
        next=addRenderableWidget(Button.builder(text("next"),b->request(page+1)).bounds(center+6,height-25,98,20).build());
        int reasonX=Math.max(8,center-180),reasonWidth=Math.max(80,Math.min(260,width-160));
        reason=addRenderableWidget(new EditBox(font,reasonX,height-25,reasonWidth,20,text("reason")));
        reason.setMaxLength(200); reason.setResponder(value->{ if(confirm!=null) confirm.active=!value.trim().isEmpty(); });
        confirm=addRenderableWidget(Button.builder(text("confirm"),b->confirmBlock()).bounds(reasonX+reasonWidth+6,height-25,64,20).build());
        cancel=addRenderableWidget(Button.builder(text("cancel"),b->cancelBlock()).bounds(reasonX+reasonWidth+76,height-25,64,20).build());
        updateControls();
    }
    private void request(int target) {
        if(minecraft.player!=null) minecraft.player.connection.sendCommand("pp paintings "+target);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta) {
        g.fill(0,0,width,height,0xff171b22);
        String heading=text("heading",page,pages,total).getString();
        g.text(font,heading,Math.max(8,(width-font.width(heading))/2),10,0xfff4e7cf);
        int columns=width>=340?2:1;
        int tileWidth=Math.min(390,(width-24-(columns-1)*12)/columns),tileHeight=132;
        int startX=(width-(tileWidth*columns+(columns-1)*12))/2,startY=30,index=0;
        for(GalleryEntry entry:entries.values()) {
            int x=startX+(index%columns)*(tileWidth+12),y=startY+(index/columns)*(tileHeight+8);
            renderEntry(g,entry,x,y,tileWidth,tileHeight,mouseX,mouseY); index++;
        }
        if(entries.size()<expected) {
            String loading=text("loading",entries.size(),expected).getString();
            g.text(font,loading,Math.max(8,(width-font.width(loading))/2),height-40,0xffaeb9c7);
        } else if(expected==0) {
            String empty=text("empty").getString();
            g.text(font,empty,Math.max(8,(width-font.width(empty))/2),height/2,0xffaeb9c7);
        }
        if(selectedMap>=0) {
            g.fill(4,height-52,width-4,height-2,0xee252c36);
            String prompt=text("reason-for",selectedMap).getString();
            g.text(font,prompt,Math.max(8,(width-font.width(prompt))/2),height-46,0xfff4e7cf);
        }
        super.extractRenderState(g,mouseX,mouseY,delta);
    }
    private void renderEntry(GuiGraphicsExtractor g,GalleryEntry entry,int x,int y,int width,int height,int mouseX,int mouseY) {
        g.fill(x,y,x+width,y+height,entry.blocked()?0xff3a2528:0xff252c36);
        byte[] pixels=entry.pixels(); int image=96;
        for(int py=0;py<image;py++) {
            int sourceY=py*128/image,px=0;
            while(px<image) {
                int sourceX=px*128/image,color=Byte.toUnsignedInt(pixels[sourceY*128+sourceX]),end=px+1;
                while(end<image && Byte.toUnsignedInt(pixels[sourceY*128+(end*128/image)])==color) end++;
                g.fill(x+8+px,y+8+py,x+8+end,y+9+py,palette[color]); px=end;
            }
        }
        int textX=x+112,max=width-120,textY=y+8;
        label(g,entry.title(),textX,textY,max,0xffffffff); textY+=14;
        label(g,text("type").getString(),textX,textY,max,0xff8bc8ff); textY+=12;
        label(g,text("id",entry.mapId()).getString(),textX,textY,max,0xffaeb9c7); textY+=12;
        label(g,text("author",entry.author()).getString(),textX,textY,max,0xffd5dbe3); textY+=12;
        label(g,text("uuid",entry.authorId()).getString(),textX,textY,max,0xff8996a7); textY+=12;
        label(g,text("created",TIME.format(Instant.ofEpochMilli(entry.createdAt()))).getString(),textX,textY,max,0xffaeb9c7); textY+=12;
        label(g,text("updated",TIME.format(Instant.ofEpochMilli(entry.updatedAt()))).getString(),textX,textY,max,0xffaeb9c7); textY+=12;
        label(g,(entry.blocked()?text("blocked-reason",entry.reason()):text("active")).getString(),textX,textY,max,
                entry.blocked()?0xffff7777:0xff79d88b); textY+=12;
        int actionY=y+height-20;
        boolean hover=selectedMap<0 && mouseX>=textX && mouseX<x+width-8 && mouseY>=actionY && mouseY<actionY+16;
        g.fill(textX,actionY,x+width-8,actionY+16,hover?0xff526173:0xff394553);
        String action=text(entry.blocked()?"unblock":"block").getString();
        label(g,action,textX+5,actionY+4,max-10,entry.blocked()?0xffffb0b0:0xfff0d58e);
    }
    private void label(GuiGraphicsExtractor g,String value,int x,int y,int max,int color) {
        g.text(font,font.plainSubstrByWidth(value,Math.max(0,max)),x,y,color);
    }
    private static Component text(String key,Object... args) { return Component.translatable("paradisepaints.gallery."+key,args); }
    @Override public boolean mouseClicked(@NonNull MouseButtonEvent event,boolean doubleClick) {
        if(event.button()==0 && selectedMap<0) {
            int columns=width>=340?2:1;
            int tileWidth=Math.min(390,(width-24-(columns-1)*12)/columns),tileHeight=132;
            int startX=(width-(tileWidth*columns+(columns-1)*12))/2,startY=30,index=0;
            for(GalleryEntry entry:entries.values()) {
                int x=startX+(index%columns)*(tileWidth+12),y=startY+(index/columns)*(tileHeight+8),textX=x+112;
                if(event.x()>=textX && event.x()<x+tileWidth-8 && event.y()>=y+tileHeight-20 && event.y()<y+tileHeight-4) {
                    if(entry.blocked()) command("pp unblock "+entry.mapId());
                    else {
                        selectedMap=entry.mapId(); reason.setValue(""); reason.setFocused(true); updateControls();
                    }
                    return true;
                }
                index++;
            }
        }
        return super.mouseClicked(event,doubleClick);
    }
    private void confirmBlock() {
        String value=reason.getValue().trim();
        if(selectedMap<0 || value.isEmpty()) return;
        command("pp block "+selectedMap+" "+value); cancelBlock();
    }
    private void cancelBlock() { selectedMap=-1; reason.setValue(""); reason.setFocused(false); updateControls(); }
    private void command(String value) { if(minecraft.player!=null) minecraft.player.connection.sendCommand(value); }
    private void updateControls() {
        boolean selecting=selectedMap>=0;
        previous.visible=!selecting && page>1; previous.active=previous.visible;
        next.visible=!selecting && page<pages; next.active=next.visible;
        reason.setVisible(selecting); reason.setEditable(selecting);
        confirm.visible=selecting; confirm.active=selecting && !reason.getValue().trim().isEmpty();
        cancel.visible=selecting; cancel.active=selecting;
    }
    @Override public boolean isPauseScreen() { return false; }
}
