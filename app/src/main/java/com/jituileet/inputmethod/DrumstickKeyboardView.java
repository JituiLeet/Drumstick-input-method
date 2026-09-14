package com.jituileet.inputmethod;

import android.graphics.*;
import android.view.*;
import java.util.*;

/** TV-first keyboard with a unified toolbar/candidate strip and large 9-key layout. */
public final class DrumstickKeyboardView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private int bg=0xFFEBECF0, fg=0xFF202124; private boolean dark, physical, mic=true, numericMode, emojiMode;
    private String preedit=""; private List<String> candidates=Collections.emptyList(); private final DrumstickImeService svc;
    private int section=2,row=0,col=0,candidatePage=0; private static final int PER_PAGE=6;
    private final String[][] qwerty={
            {"q","w","e","r","t","y","u","i","o","p"},
            {"a","s","d","f","g","h","j","k","l"},
            {"z","x","c","v","b","n","m","中/英","⌫"},
            {"?123","，","🌐","空格","。","←","→","↵"}
    };
    // 9-key follows the requested three-column layout; the bottom function row
    // is the same as the original 26-key keyboard.
    private final String[][] nine={
            {"？！。","ABC","DEF"},
            {"GHI","JKL","MNO"},
            {"PQRS","TUV","WXYZ"}
    };
    // Functions beside the 9-key area. The bottom row remains the same as 26-key.
    private final String[] nineSide={"中/英","⌫","↵"};
    private final String[] nineBottom={"?123","，","🌐","空格","。","←","→"};
    private final String[][] symbols={
            {"1","2","3","4","5","6","7","8","9","0"},
            {"@","#","$","%","&","*","(",")","-","+"},
            {"~","`","!","?","/","=","_","\"","'","⌫"},
            {"?123","，",":","；","（","）","【","】","。","↵"},
            {"表情","空格","<",">","|","\\","^","·"}
    };
    private final String[] emojis={"😀","😂","🤣","😊","😍","😘","😎","😭","😡","👍","👎","👏","🙏","❤️","💔","🎉","🔥","⭐","✨","💯","🤔","😴","🤗","😮","😱","🥳","🤩","🙌","👌","✌️","💪","❤️‍🔥"};

    public DrumstickKeyboardView(DrumstickImeService c){super(c);svc=c;setFocusable(true);setFocusableInTouchMode(true);}
    private String[] tools(){ArrayList<String>a=new ArrayList<>();a.add("设置");a.add("复制");a.add("剪贴板");if(mic)a.add("语音");a.add("隐藏");return a.toArray(new String[0]);}
    private String rl(String s){if(Prefs.language(getContext()).equals("en")){if(s.equals("中/英"))return engineChinese()?"中/英":"英/中";if(s.equals("空格"))return "Space";if(s.equals("↵"))return "Enter";if(s.equals("⌫"))return "Backspace";if(s.equals("🌐"))return "Mode";if(s.equals("表情"))return "Emoji";}return s;}
    public void setColors(int c,boolean d){bg=c;dark=d;fg=d?0xFFF1F3F4:0xFF202124;invalidate();}
    public void setMicVisible(boolean v){mic=v;invalidate();}
    public void setPhysical(boolean v){physical=v;invalidate();requestLayout();}
    public void setNumericMode(boolean v){numericMode=v;emojiMode=false;invalidate();}
    public void setEmojiMode(boolean v){
        if(!v){ emojiMode=false; numericMode=true; section=2; row=0; col=0; candidatePage=0; invalidate(); requestFocus(); return; }
        // Emoji is opened only from ?123. Returning always goes back to ?123.
        emojiMode=true; numericMode=false; section=2; row=0; col=0; invalidate(); requestFocus();
    }
    /** Reset transient UI state whenever Android starts a new IME session. */
    public void resetForInputSession(){
        // Re-enter the selected 26-key or 9-key base keyboard. Never restore transient pages.
        emojiMode=false; numericMode=false; section=2; row=0; col=0; candidatePage=0;
        invalidate(); requestFocus();
    }
    public void setKeyboardLayout(int mode){numericMode=false;emojiMode=false;if(mode==0||mode==1)Prefs.keyboardLayout(getContext(),mode);section=2;row=0;col=0;candidatePage=0;invalidate();requestFocus();}
    public void setCandidates(List<String> c,String pre){
        String nextPre=pre==null?"":pre; boolean changed=!nextPre.equals(preedit); candidates=c==null?Collections.<String>emptyList():new ArrayList<>(c); preedit=nextPre;
        if(changed)candidatePage=0; int max=Math.max(0,(candidates.size()-1)/PER_PAGE); if(candidatePage>max)candidatePage=max;
        if(!preedit.isEmpty()&&!candidates.isEmpty()){section=1;row=0;col=0;} else if(preedit.isEmpty()&&section==1){section=0;row=0;col=0;}
        invalidate();
    }
    @Override protected void onMeasure(int ws,int hs){int w=MeasureSpec.getSize(ws);float d=getResources().getDisplayMetrics().density;int h=(int)((physical?76:300)*d);setMeasuredDimension(w,h);}
    @Override protected void onDraw(Canvas c){p.setStyle(Paint.Style.FILL);p.setColor(physical?0xFFEBECF0:(dark?0xFF202124:bg));c.drawRect(0,0,getWidth(),getHeight(),p);if(physical)drawPhysical(c);else drawNormal(c);}

    private void drawNormal(Canvas c){
        float topH=Math.max(44,getHeight()*0.15f);p.setColor(dark?0xFF303134:0xFFF6F7F9);c.drawRect(0,0,getWidth(),topH,p);
        if(!preedit.isEmpty()&&!candidates.isEmpty())drawCandidates(c,topH);else drawTools(c,topH);
        float y=topH;
        if(emojiMode){drawEmojiToolbar(c,topH); drawEmoji(c,y); return;}
        int layout=Prefs.keyboardLayout(getContext());
        if(layout==1){drawNine(c,y);return;}
        String[][] rr=numericMode?symbols:qwerty;
        float rh=(getHeight()-y)/rr.length;
        for(int r=0;r<rr.length;r++) drawRow(c,rr[r],y+r*rh,rh,r);
    }

    private String displayKey(String key){
        if(key.equals("中/英") && !engineChinese()) return "中/英";
        if(key.equals("中/英") && engineChinese() && !preedit.isEmpty()) return "分词";
        return key;
    }
    private boolean engineChinese(){ return svc.isChineseInputMode(); }
    private void drawRow(Canvas c,String[] rowKeys,float y,float rh,int r){
        float gap=4,total=getWidth()-gap*(rowKeys.length+1),kw=total/rowKeys.length,xx=gap;
        for(int j=0;j<rowKeys.length;j++){String key=rowKeys[j];float w=kw;boolean f=section==2&&this.row==r&&this.col==j;drawKey(c,rl(displayKey(key)),xx,y+gap,w,rh-gap*2,f);xx+=w+gap;}
    }

    private void drawNine(Canvas c,float y){
        float gap=6;
        float upperH=getHeight()-y;
        // Three 9-key rows on the left; three vertical function keys on the right.
        // The bottom function row spans the full width, matching the 26-key keyboard.
        float bottomH=Math.max(48, upperH*0.23f);
        float bodyH=upperH-bottomH-gap;
        float sideW=Math.max(112,getWidth()*0.17f);
        float leftW=getWidth()-sideW-gap;
        float rh=(bodyH-gap*4)/3f;
        float kw=(leftW-gap*4)/3f;
        for(int r=0;r<3;r++){
            for(int j=0;j<3;j++){
                String key=nine[r][j];
                float x=gap+j*(kw+gap), yy=y+gap+r*(rh+gap);
                boolean f=section==2&&row==r&&col==j;
                drawKey(c,rl(displayKey(key)),x,yy,kw,rh,f);
            }
        }
        float sideRh=(bodyH-gap*4)/3f;
        for(int i=0;i<3;i++){
            float x=leftW+gap, yy=y+gap+i*(sideRh+gap);
            boolean f=section==2&&row==i&&col==3;
            drawKey(c,rl(displayKey(nineSide[i])),x,yy,sideW-gap*2,sideRh,f);
        }
        float bw=(getWidth()-gap*(nineBottom.length+1))/nineBottom.length;
        float by=y+bodyH+gap;
        for(int j=0;j<nineBottom.length;j++){
            boolean f=section==2&&row==3&&col==j;
            drawKey(c,rl(displayKey(nineBottom[j])),gap+j*(bw+gap),by,bw,bottomH-gap*2,f);
        }
    }

    private void drawEmojiToolbar(Canvas c,float h){
        p.setColor(dark?0xFF303134:0xFFF6F7F9);
        c.drawRect(0,0,getWidth(),h,p);
        p.setColor(fg); p.setTextSize(18);
        String t=Prefs.language(getContext()).equals("en")?"Emoji":"表情";
        float tw=p.measureText(t); c.drawText(t,(getWidth()-tw)/2,h*0.68f,p);
    }

    private void drawEmoji(Canvas c,float y){
        float gap=8; int cols=8; float rh=(getHeight()-y-gap*5)/4f; float kw=(getWidth()-gap*(cols+1))/cols;
        for(int i=0;i<32;i++){
            int r=i/cols,j=i%cols;
            float x=gap+j*(kw+gap), yy=y+gap+r*(rh+gap);
            boolean f=section==2&&row==r&&col==j;
            if(i==31){ drawIconKey(c,x,yy,kw,rh,f); }
            else { drawKey(c,emojis[i],x,yy,kw,rh,f); }
        }
    }

    private void drawTools(Canvas c,float h){String[] a=tools();float w=getWidth()/(float)a.length;for(int i=0;i<a.length;i++){if(section==0&&col==i){p.setColor(dark?0x555F8DFF:0x443366FF);c.drawRoundRect(i*w+2,2,(i+1)*w-2,h-2,8,8,p);}drawIcon(c,a[i],i*w,0,w,h);}}
    private void drawCandidates(Canvas c,float h){p.setTextSize(23);int from=candidatePage*PER_PAGE,to=Math.min(candidates.size(),from+PER_PAGE);float x=10;p.setColor(fg);for(int i=from;i<to;i++){String s=candidates.get(i);float w=Math.max(56,p.measureText(s)+24);if(section==1&&col==i-from){p.setColor(dark?0x554A90E2:0x333366CC);c.drawRoundRect(x,3,x+w,h-3,8,8,p);p.setColor(fg);}c.drawText(s,x+12,h*0.68f,p);x+=w+4;}p.setTextSize(13);p.setColor(dark?0xFFB0B4BB:0xFF6B6F76);c.drawText(preedit,10,h*0.30f,p);}
    private void drawKey(Canvas c,String s,float x,float y,float w,float h,boolean focused){p.setColor(dark?0xFF303134:0xFFF7F8FA);c.drawRoundRect(x,y,x+w,y+h,8,8,p);if(focused){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);p.setColor(dark?0xFFB9C8FF:0xFF5B6FB5);c.drawRoundRect(x+1,y+1,x+w-1,y+h-1,8,8,p);p.setStyle(Paint.Style.FILL);}p.setColor(fg);if(s.equals("⌫")||s.equals("Backspace")){drawBackspace(c,x,y,w,h);return;}if(s.equals("↵")||s.equals("Enter")){drawEnter(c,x,y,w,h);return;}p.setTextSize(s.length()>6?14:(s.contains("\n")?18:20));float tw=p.measureText(s);c.drawText(s,x+(w-tw)/2,y+h/2-(p.ascent()+p.descent())/2,p);}
    private void drawBackspace(Canvas c,float x,float y,float w,float h){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);float bx=x+w*.18f,by=y+h*.24f,bw=w*.64f,bh=h*.52f;Path q=new Path();q.moveTo(bx+bw*.20f,by);q.lineTo(bx+bw*.08f,by+bh*.5f);q.lineTo(bx+bw*.20f,by+bh);q.lineTo(bx+bw*.78f,by+bh);q.lineTo(bx+bw*.86f,by+bh*.38f);q.lineTo(bx+bw*.78f,by);q.close();c.drawPath(q,p);c.drawLine(bx+bw*.42f,by+bh*.35f,bx+bw*.62f,by+bh*.65f,p);c.drawLine(bx+bw*.62f,by+bh*.35f,bx+bw*.42f,by+bh*.65f,p);p.setStyle(Paint.Style.FILL);}
    private void drawEnter(Canvas c,float x,float y,float w,float h){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.2f);float ex=x+w*.22f,ey=y+h*.25f,ew=w*.56f,eh=h*.5f;Path q=new Path();q.moveTo(ex+ew*.72f,ey+eh*.15f);q.lineTo(ex+ew*.72f,ey+eh*.62f);q.lineTo(ex+ew*.22f,ey+eh*.62f);q.moveTo(ex+ew*.22f,ey+eh*.62f);q.lineTo(ex+ew*.38f,ey+eh*.42f);q.moveTo(ex+ew*.22f,ey+eh*.62f);q.lineTo(ex+ew*.38f,ey+eh*.82f);c.drawPath(q,p);p.setStyle(Paint.Style.FILL);}
    private void drawIconKey(Canvas c,float x,float y,float w,float h,boolean focused){
        p.setColor(dark?0xFF303134:0xFFF7F8FA);
        c.drawRoundRect(x,y,x+w,y+h,8,8,p);
        if(focused){
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3); p.setColor(dark?0xFFB9C8FF:0xFF5B6FB5);
            c.drawRoundRect(x+1,y+1,x+w-1,y+h-1,8,8,p); p.setStyle(Paint.Style.FILL);
        }
        p.setColor(fg);
        float cx=x+w/2f, cy=y+h/2f;
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3f);
        Path q=new Path(); q.moveTo(cx+9,cy-10); q.lineTo(cx-4,cy); q.lineTo(cx+9,cy+10);
        c.drawPath(q,p); c.drawLine(cx-4,cy,cx+13,cy,p);
        p.setStyle(Paint.Style.FILL);
    }

    private void drawIcon(Canvas c,String s,float x,float y,float w,float h){p.setColor(fg);float cx=x+w/2,cy=y+h/2;if(s.equals("设置")){for(int r=0;r<2;r++)for(int j=0;j<2;j++)c.drawRoundRect(cx-17+j*22,cy-17+r*22,cx+1+j*22,cy+1+r*22,3,3,p);return;}if(s.equals("复制")){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.5f);c.drawRect(cx-11,cy-9,cx+11,cy+12,p);c.drawRect(cx-5,cy-14,cx+16,cy+7,p);p.setStyle(Paint.Style.FILL);return;}if(s.equals("剪贴板")){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.5f);c.drawRoundRect(cx-12,cy-10,cx+12,cy+14,3,3,p);c.drawRoundRect(cx-6,cy-15,cx+6,cy-7,3,3,p);p.setStyle(Paint.Style.FILL);return;}if(s.equals("语音")){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);c.drawRoundRect(cx-6,cy-15,cx+6,cy+4,7,7,p);c.drawArc(cx-13,cy-3,cx+13,cy+15,0,180,false,p);c.drawLine(cx,cy+15,cx,cy+22,p);c.drawLine(cx-8,cy+22,cx+8,cy+22,p);p.setStyle(Paint.Style.FILL);return;}Path q=new Path();q.moveTo(cx-11,cy-5);q.lineTo(cx+11,cy-5);q.lineTo(cx,cy+9);q.close();c.drawPath(q,p);}
    private void drawPhysical(Canvas c){p.setTypeface(Typeface.DEFAULT);p.setColor(0xFF303238);if(!preedit.isEmpty()&&!candidates.isEmpty()){p.setTextSize(20);float x=18;int from=candidatePage*PER_PAGE,to=Math.min(candidates.size(),from+PER_PAGE);for(int i=from;i<to;i++){String s=candidates.get(i);float w=Math.max(64,p.measureText(s)+28);if(section==1&&col==i-from){p.setColor(0x223366CC);c.drawRoundRect(x,4,x+w,getHeight()-4,8,8,p);p.setColor(0xFF303238);}c.drawText(s,x+12,getHeight()/2+7,p);x+=w+4;}return;}String title=Prefs.language(getContext()).equals("en")?"Drumstick Input Method":"鸡腿输入法";p.setTextSize(20);p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));float tw=p.measureText(title);float ix=getWidth()-58;drawAppIcon(c,ix,getHeight()/2-22,44,44);p.setColor(0xFF303238);c.drawText(title,ix-tw-12,getHeight()/2+7,p);p.setTypeface(Typeface.DEFAULT);}
    private void drawAppIcon(Canvas c,float x,float y,float w,float h){p.setColor(Color.WHITE);c.drawRoundRect(x,y,x+w,y+h,6,6,p);p.setColor(0xFF4A4A4A);for(int r=0;r<3;r++)for(int j=0;j<3;j++)c.drawRoundRect(x+7+j*10,y+7+r*9,x+15+j*10,y+14+r*9,1,1,p);}

    private int rowCount(){
        if(emojiMode)return 4;
        int layout=Prefs.keyboardLayout(getContext());
        if(layout==1)return numericMode?symbols.length:4;
        return numericMode?symbols.length:qwerty.length;
    }
    private int rowLen(int r){
        if(emojiMode)return 8;
        int layout=Prefs.keyboardLayout(getContext());
        if(layout==1){
            if(numericMode) return symbols[r].length;
            return r<3?3:sevenBottomLength();
        }
        return numericMode?symbols[r].length:qwerty[r].length;
    }
    private int sevenBottomLength(){ return nineBottom.length; }

    private int toolForKey(int keyCol){int n=tools().length;return Math.max(0,Math.min(n-1,Math.round(keyCol*(n-1)/9f)));}
    private int keyForTool(int tool){int n=9;return Math.max(0,Math.min(n-1,Math.round(tool*9f/Math.max(1,tools().length-1))));}

    private void moveHorizontal(int d){
        if(section==0){col=Math.max(0,Math.min(tools().length-1,col+d));return;}
        if(section==1){int count=Math.min(PER_PAGE,candidates.size()-candidatePage*PER_PAGE);if(count<=0)return;if(d>0&&col==count-1&&candidatePage<(candidates.size()-1)/PER_PAGE){svc.engineChangePage(false);candidatePage++;col=0;}else if(d<0&&col==0&&candidatePage>0){svc.engineChangePage(true);candidatePage--;col=0;}else col=Math.max(0,Math.min(count-1,col+d));return;}
        if(Prefs.keyboardLayout(getContext())==1 && !numericMode && !emojiMode){
            if(row<3){
                if(d>0){
                    if(col<2) col++; else if(col==2) col=3;
                    else { row=Math.min(2,row+1); col=0; }
                }else{
                    if(col==3) col=2;
                    else if(col>0) col--;
                    else if(row>0){row--;col=2;}
                }
                return;
            }
            if(row==3){
                col=Math.max(0,Math.min(nineBottom.length-1,col+d));
                return;
            }
        }
        int len=rowLen(row);
        if(d>0){if(col<len-1)col++;else if(row<rowCount()-1){row++;col=0;}}
        else {if(col>0)col--;else if(row>0){row--;col=Math.min(col,rowLen(row)-1);}}
    }
    private void moveVertical(int d){
        if(section==0&&d>0){section=2;row=0;col=Math.min(rowLen(0)-1,keyForTool(col));return;}
        if(section==1){
            if(d>0){section=2;row=0;col=0;}
            else { section=0;col=0; }
            return;
        }
        if(section==2){
            if(emojiMode){
                row=Math.max(0,Math.min(rowCount()-1,row+d)); return;
            }
            if(Prefs.keyboardLayout(getContext())==1 && !numericMode && !emojiMode){
                if(d>0){
                    if(row<2){row++;col=Math.min(col,3);}
                    else if(row==2){row=3;col=Math.min(col,nineBottom.length-1);}
                }else{
                    if(row==3){row=2;col=Math.min(col,2);}
                    else if(row>0){row--;col=Math.min(col,3);}
                    else {section=0;col=Math.min(tools().length-1,keyForTool(col));}
                }
                return;
            }
            int nr=row+d;
            // UP from the first keyboard row moves to the unified top toolbar.
            // DOWN from the toolbar still returns to the keyboard/candidate area.
            if(nr<0){section=0;col=Math.min(tools().length-1,keyForTool(col));return;}
            if(nr<rowCount()){
                row=nr;
                col=Math.min(col,rowLen(row)-1);
            }
        }
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e){
        if(e.getAction()!=KeyEvent.ACTION_DOWN)return true;int k=e.getKeyCode();
        if(k==KeyEvent.KEYCODE_PAGE_UP||k==KeyEvent.KEYCODE_PAGE_DOWN){if(candidates.isEmpty())return true;boolean b=k==KeyEvent.KEYCODE_PAGE_UP;if(svc.engineChangePage(b))candidatePage=Math.max(0,Math.min((candidates.size()-1)/PER_PAGE,candidatePage+(b?-1:1)));else candidatePage=Math.max(0,Math.min((candidates.size()-1)/PER_PAGE,candidatePage+(b?-1:1)));section=1;col=0;invalidate();return true;}
        if(k==KeyEvent.KEYCODE_DPAD_LEFT){moveHorizontal(-1);invalidate();return true;}if(k==KeyEvent.KEYCODE_DPAD_RIGHT){moveHorizontal(1);invalidate();return true;}if(k==KeyEvent.KEYCODE_DPAD_UP){moveVertical(-1);invalidate();return true;}if(k==KeyEvent.KEYCODE_DPAD_DOWN){moveVertical(1);invalidate();return true;}if(k==KeyEvent.KEYCODE_DPAD_CENTER||k==KeyEvent.KEYCODE_ENTER){activate();return true;}return true;
    }

    private void activate(){
        if(section==0){
            String[]a=tools();if(col>=0&&col<a.length)svc.press(a[col]);return;
        }
        if(section==1){int idx=candidatePage*PER_PAGE+col;if(idx>=0&&idx<candidates.size()){String out=svc.engineChoose(idx);if(out!=null&&!out.isEmpty())svc.commitEngineResult(out);}return;}
        if(emojiMode){
            int idx=row*8+col;
            if(idx==31){ setEmojiMode(false); return; }
            if(idx>=0&&idx<emojis.length)svc.press(emojis[idx]);
            return;
        }
        int layout=Prefs.keyboardLayout(getContext());
        if(layout==1){
            String raw;
            if(row<3 && col<3) raw=nine[row][col];
            else if(row<3 && col==3) raw=nineSide[row];
            else if(row==3 && col>=0 && col<nineBottom.length) raw=nineBottom[col];
            else return;
            if(raw==null||raw.isEmpty())return;
            svc.press(raw);
            return;
        }
        String key=(numericMode?symbols:qwerty)[row][col];svc.press(key);
    }

    @Override public boolean onTouchEvent(MotionEvent e){
        if(e.getAction()!=MotionEvent.ACTION_UP)return true;
        float topH=Math.max(44,getHeight()*0.15f);
        if(e.getY()<topH){
            if(emojiMode){ return true; }
            if(!preedit.isEmpty()&&!candidates.isEmpty()){
                float x=8;p.setTextSize(23);
                int from=candidatePage*PER_PAGE,to=Math.min(candidates.size(),from+PER_PAGE);
                for(int i=from;i<to;i++){
                    float w=Math.max(60,p.measureText(candidates.get(i))+28);
                    if(e.getX()>=x&&e.getX()<x+w){
                        col=i-from;
                        activate();
                        return true;
                    }
                    x+=w+4;
                }
            }else{
                String[]a=tools();
                int i=(int)(e.getX()/(getWidth()/(float)a.length));
                if(i>=0&&i<a.length){
                    section=0;
                    col=i;
                    svc.press(a[i]);
                }
            }
            return true;
        }
        if(emojiMode){
            float y=topH;
            float gap=8;
            int cols=8;
            float rh=(getHeight()-y-gap*5)/4f;
            float kw=(getWidth()-gap*(cols+1))/cols;
            int r=(int)((e.getY()-y-gap)/(rh+gap));
            int cc=(int)((e.getX()-gap)/(kw+gap));
            if(r>=0&&r<4&&cc>=0&&cc<cols){
                row=r;
                col=cc;
                section=2;
                activate();
            }
            return true;
        }
        int layout=Prefs.keyboardLayout(getContext());
        float y=topH;
        if(layout==1){
            if(numericMode){
                String[][] rr=symbols;
                float rh=(getHeight()-y)/rr.length;
                int rridx=(int)((e.getY()-y)/rh);
                if(rridx>=0&&rridx<rr.length){
                    float gap=4; float total=getWidth()-gap*(rr[rridx].length+1);
                    float kw=total/rr[rridx].length;
                    int cc=(int)((e.getX()-gap)/(kw+gap));
                    if(cc>=0&&cc<rr[rridx].length){row=rridx;col=cc;section=2;activate();}
                }
                return true;
            }
            float gap=6;
            float upperH=getHeight()-y;
            float bottomH=Math.max(48,upperH*0.23f);
            float bodyH=upperH-bottomH-gap;
            float sideW=Math.max(112,getWidth()*0.17f);
            float leftW=getWidth()-sideW-gap;
            if(e.getY()>=y+bodyH+gap){
                float bw=(getWidth()-gap*(nineBottom.length+1))/nineBottom.length;
                int cc=(int)((e.getX()-gap)/(bw+gap));
                if(cc>=0&&cc<nineBottom.length){row=3;col=cc;section=2;activate();}
                return true;
            }
            float rh=(bodyH-gap*4)/3f;
            int r=(int)((e.getY()-y-gap)/(rh+gap));
            if(r<0||r>=3)return true;
            if(e.getX()>=leftW){
                float sideRh=(bodyH-gap*4)/3f;
                int sr=(int)((e.getY()-y-gap)/(sideRh+gap));
                if(sr>=0&&sr<3){row=sr;col=3;section=2;activate();}
                return true;
            }
            float kw=(leftW-gap*4)/3f;
            int cc=(int)((e.getX()-gap)/(kw+gap));
            if(cc>=0&&cc<3){row=r;col=cc;section=2;activate();}
            return true;
        }
        String[][] rr=numericMode?symbols:qwerty;
        int rridx=(int)((e.getY()-y)/((getHeight()-y)/rr.length));
        if(rridx>=0&&rridx<rr.length){
            float gap=4;
            float total=getWidth()-gap*(rr[rridx].length+1);
            float kw=total/rr[rridx].length;
            int cc=(int)((e.getX()-gap)/(kw+gap));
            if(cc>=0&&cc<rr[rridx].length){
                row=rridx;
                col=cc;
                section=2;
                activate();
            }
        }
        return true;
    }
            }

