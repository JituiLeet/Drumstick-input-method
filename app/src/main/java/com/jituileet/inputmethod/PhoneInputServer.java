package com.jituileet.inputmethod;

import android.content.Context;
import java.io.*;
import java.net.*;
import java.util.*;

/** Small LAN HTTP endpoint for phone-to-TV text input. */
public final class PhoneInputServer {
    public interface Listener { void onText(String text); }
    private final Context context;
    private final Listener listener;
    private volatile boolean running;
    private ServerSocket server;
    private Thread thread;
    private int port;

    public PhoneInputServer(Context c, Listener l) { context=c.getApplicationContext(); listener=l; }

    public synchronized void start() {
        if (running) return;
        int[] candidates = {8080, 8081, 8888, 18080};
        for (int p : candidates) {
            try {
                server = new ServerSocket(p, 16, InetAddress.getByName("0.0.0.0"));
                port = p; running=true; break;
            } catch (IOException ignored) {}
        }
        if (!running) {
            try { server = new ServerSocket(0, 16, InetAddress.getByName("0.0.0.0")); port=server.getLocalPort(); running=true; }
            catch (IOException ignored) { return; }
        }
        thread=new Thread(this::loop,"drumstick-phone-input"); thread.start();
    }

    public synchronized void stop() { running=false; try { if(server!=null) server.close(); } catch(Exception ignored) {} }
    public boolean isRunning(){ return running; }
    public String getUrl(){ String ip=localIp(); return running&&port>0&&!ip.isEmpty() ? "http://"+ip+":"+port+"/" : ""; }

    private void loop(){
        while(running){
            try(Socket s=server.accept()){ handle(s); }
            catch(IOException e){ if(running) { /* next connection */ } }
        }
    }

    private void handle(Socket s)throws IOException{
        BufferedReader r=new BufferedReader(new InputStreamReader(s.getInputStream(),"UTF-8"));
        String first=r.readLine(); if(first==null)return;
        int len=0; String line;
        while((line=r.readLine())!=null&&!line.isEmpty()){
            String z=line.toLowerCase(Locale.US);
            if(z.startsWith("content-length:"))try{len=Integer.parseInt(line.substring(15).trim());}catch(Exception ignored){}
        }
        String body="";
        if(first.startsWith("POST")&&len>0){
            char[] b=new char[Math.min(len,65536)]; int n=r.read(b); if(n>0)body=new String(b,0,n);
        }
        if(body.startsWith("text=")){String text=URLDecoder.decode(body.substring(5),"UTF-8");if(!text.isEmpty()&&listener!=null)listener.onText(text);}
        String html="<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'><style>body{font-family:sans-serif;background:#ebecf0;padding:24px}textarea{width:100%;height:45vh;font-size:22px;box-sizing:border-box}button{font-size:20px;padding:12px 22px}</style><h2>鸡腿输入法 · 手机输入</h2><form method='post'><textarea name='text' autofocus placeholder='在这里输入文字'></textarea><p><button type='submit'>发送到电视</button></p></form>";
        byte[] data=html.getBytes("UTF-8"); OutputStream o=s.getOutputStream();
        o.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: "+data.length+"\r\nConnection: close\r\n\r\n").getBytes("UTF-8")); o.write(data); o.flush();
    }

    private String localIp(){
        // Prefer Android's Wi-Fi interface address, then fall back to all active IPv4 interfaces.
        try{
            android.net.wifi.WifiManager wm=(android.net.wifi.WifiManager)context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if(wm!=null){ int v=wm.getConnectionInfo().getIpAddress(); if(v!=0){ return ((v&0xff))+"."+((v>>8)&0xff)+"."+((v>>16)&0xff)+"."+((v>>24)&0xff); } }
        }catch(Throwable ignored){}
        try{
            Enumeration<NetworkInterface> es=NetworkInterface.getNetworkInterfaces();
            while(es.hasMoreElements()){
                NetworkInterface ni=es.nextElement(); if(!ni.isUp()||ni.isLoopback())continue;
                Enumeration<InetAddress> as=ni.getInetAddresses();
                while(as.hasMoreElements()){
                    InetAddress a=as.nextElement();
                    if(!a.isLoopbackAddress()&&!a.isLinkLocalAddress()&&a instanceof Inet4Address)return a.getHostAddress();
                }
            }
        }catch(Exception ignored){}
        return "";
    }
}
