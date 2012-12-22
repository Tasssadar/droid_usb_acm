package com.tassadar.usb_acm;

import android.os.Handler;
import android.os.Message;
import com.tassadar.usb_acm.SerialDevice.SerialDeviceListener;
import java.io.IOException;
import java.lang.ref.WeakReference;


public class PollingThread extends Thread {

    public static final int POLLING_TIME = 10;
    public static final long POLLING_BUFF_TIME_NANO = 50*1000*1000;

    public static final int DEFAULT_POLL_BUFFER_SIZE = 16 * 1024;
    
    public PollingThread(SerialDevice dev) {
        m_dev = dev;
        m_handler = new PollingDataHandler(dev.getListener());
        m_run = false;
    }

    @Override
    public void run() {
        final byte[] buff = new byte[DEFAULT_POLL_BUFFER_SIZE];
        int len = 0;
        long lastSend = System.nanoTime();
        long curr;
        m_run = true;

        while(m_run) {
            try {
                len += m_dev.read(buff, len, 50);                
            } catch (IOException e) { }
            
            curr = System.nanoTime();
            // FIXME: remove this buffer?
            if(curr - lastSend > POLLING_BUFF_TIME_NANO)
            {
                if(len > 0) {
                    byte[] data = new byte[len];
                    System.arraycopy(buff, 0, data, 0, len);
                    m_handler.obtainMessage(0,  data).sendToTarget();
                }
                
                len = 0;
                lastSend = curr;
            }

            try {
                Thread.sleep(POLLING_TIME, 0);
            } catch (InterruptedException e) { }
        }
    }

    public void stopPolling() {
        m_run = false;
        try {
            join();
        } catch (InterruptedException e) { }
    }
    
    public void setListener(SerialDeviceListener listener) {
        m_handler.setListener(listener);
        if(listener != null && !m_run)
            start();
        else if(listener == null && m_run)
            stopPolling();
    }
    
    static private class PollingDataHandler extends Handler {
        private volatile WeakReference<SerialDeviceListener> m_listener;

        public PollingDataHandler(SerialDeviceListener listener) {
            setListener(listener);
        }
        
        public void setListener(SerialDeviceListener listener) {
            m_listener = new WeakReference<SerialDeviceListener>(listener);
        }

        @Override
        public void handleMessage(Message msg) {
            SerialDeviceListener l = m_listener.get();
            if(l != null)
                l.onDataRead((byte[])msg.obj);
        }
    }

    private PollingDataHandler m_handler;
    private volatile boolean m_run;
    private SerialDevice m_dev;
}
