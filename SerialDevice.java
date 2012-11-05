package com.tassadar.usb_acm;

import java.io.IOException;
import java.lang.ref.WeakReference;

import android.annotation.TargetApi;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.os.Handler;
import android.os.Message;

@TargetApi(12)
public abstract class SerialDevice {

    public static final int POLLING_TIME = 50;

    public static final int DEFAULT_READ_BUFFER_SIZE = 16 * 1024;
    public static final int DEFAULT_WRITE_BUFFER_SIZE = 16 * 1024;
    public static final int DEFAULT_POLL_BUFFER_SIZE = 16 * 1024;

    public static final int TYPE_UNK     = -1;
    public static final int TYPE_CDC_ACM = 1;
    public static final int TYPE_FTDI    = 2;
    
    public static SerialDevice create(UsbDevice dev, UsbDeviceConnection conn) {
        int type = SerialDeviceMgr.matchRawDeviceToType(dev);
        
        switch(type) {
        case TYPE_CDC_ACM:
            return new CdcAcmDevice(dev, conn);
        }
        return null;
    }

    protected SerialDevice(UsbDevice dev, UsbDeviceConnection conn) {
        m_dev = dev;
        m_conn = conn;

        m_readBuff = new byte[DEFAULT_READ_BUFFER_SIZE];
        m_writeBuff = new byte[DEFAULT_WRITE_BUFFER_SIZE];
    }
    
    public void closeUsbDevConn() {
        m_conn.close();
    }

    abstract public int getType();
    
    abstract public void open() throws IOException;
    public void close() {
        if(m_pollingThread != null)
            m_pollingThread.stopPolling();
        closeUsbDevConn();
    }
    
    abstract public int read(byte[] dest, int timeoutMs) throws IOException;
    abstract public int write(byte[] src, int timeoutMs) throws IOException;
    
    public void startPolling(SerialDeviceListener listener) {
        if(m_pollingThread != null)
            return;

        m_listener = listener;
        m_pollingThread = new PollingThread();
        m_pollingThread.start();
    }
    
    public interface SerialDeviceListener {
        void onDataRead(byte[] data);
    }

    private class PollingThread extends Thread {

        public PollingThread() {
            m_handler = new PollingDataHandler(m_listener);
        }

        @Override
        public void run() {
            final byte[] buff = new byte[DEFAULT_POLL_BUFFER_SIZE];
            int len;

            if(m_listener == null)
                return;

            m_run = true;

            while(m_run) {
                
                try {
                    len = read(buff, 50);
                    if(len > 0)
                    {
                        byte[] data = new byte[len];
                        System.arraycopy(buff, 0, data, 0, len);
                        m_handler.obtainMessage(0,  data).sendToTarget();
                    }
                } catch (IOException e) { }
                
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

        private PollingDataHandler m_handler;
        private volatile boolean m_run;
    }

    static private class PollingDataHandler extends Handler {
        private final WeakReference<SerialDeviceListener> m_listener;

        public PollingDataHandler(SerialDeviceListener listener) {
            m_listener = new WeakReference<SerialDeviceListener>(listener);
        }

        @Override
        public void handleMessage(Message msg) {
            SerialDeviceListener l = m_listener.get();
            if(l != null)
                l.onDataRead((byte[])msg.obj);
        }
    }

    protected UsbDevice m_dev;
    protected UsbDeviceConnection m_conn;

    protected final Object m_readLock = new Object();
    protected final Object m_writeLock = new Object();
    protected byte[] m_readBuff;
    protected byte[] m_writeBuff;

    protected int m_baudrate;
    protected int m_stopBits;
    protected int m_parity;
    protected int m_dataBits;
    
    private SerialDeviceListener m_listener;
    private PollingThread m_pollingThread;
}