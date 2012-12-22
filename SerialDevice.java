package com.tassadar.usb_acm;

import java.io.IOException;

import android.annotation.TargetApi;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;

@TargetApi(12)
public abstract class SerialDevice {

    public static final int DEFAULT_READ_BUFFER_SIZE = 16 * 1024;
    public static final int DEFAULT_WRITE_BUFFER_SIZE = 16 * 1024;

    public static final int TYPE_UNK     = -1;
    public static final int TYPE_CDC_ACM = 1;
    public static final int TYPE_FTDI    = 2;
    
    public static SerialDevice create(UsbDevice dev, SerialDeviceMgr mgr) {
        int type = SerialDeviceMgr.matchRawDeviceToType(dev);
        
        switch(type) {
        case TYPE_CDC_ACM:
            return new CdcAcmDevice(dev, mgr);
        }
        return null;
    }

    protected SerialDevice(UsbDevice dev, SerialDeviceMgr mgr) {
        m_dev = dev;
        m_mgr = mgr;

        m_readBuff = new byte[DEFAULT_READ_BUFFER_SIZE];
        m_writeBuff = new byte[DEFAULT_WRITE_BUFFER_SIZE];
        
        m_pollingThread = new PollingThread(this);
        
        m_open = false;
    }
    
    public void closeUsbDevConn() {
        m_conn.close();
    }

    abstract public int getType();
    
    abstract protected void openDevice() throws IOException;
    public void open(SerialDeviceListener listener) throws IOException {
        if(m_open)
            return;
        
        m_conn = m_mgr.openDevConnection(m_dev);
        if(m_conn == null)
            throw new IOException("Couldn't open SerialDevice: failed to open UsbDeviceConnection");

        openDevice();
        setListener(listener);
        m_open = true;
    }

    public void close() {
        if(!m_open)
            return;

        m_pollingThread.stopPolling();
        m_open = false;
        closeUsbDevConn();
    }
    
    public boolean isOpen() { return m_open; }
    
    abstract public int read(byte[] dest, int offset, int timeoutMs) throws IOException;
    abstract public int write(byte[] src, int timeoutMs) throws IOException;

    public interface SerialDeviceListener {
        void onDataRead(byte[] data);
    }
    
    public SerialDeviceListener getListener() { return m_listener; }
    public void setListener(SerialDeviceListener listener) {
        m_listener = listener;
        m_pollingThread.setListener(listener);
    }
    
    public UsbDevice getUsbDevice() { return m_dev; }

    protected UsbDevice m_dev;
    protected UsbDeviceConnection m_conn;
    protected SerialDeviceMgr m_mgr;

    protected final Object m_readLock = new Object();
    protected final Object m_writeLock = new Object();
    protected byte[] m_readBuff;
    protected byte[] m_writeBuff;

    protected int m_baudrate;
    protected int m_stopBits;
    protected int m_parity;
    protected int m_dataBits;
    
    protected boolean m_open;
    
    private SerialDeviceListener m_listener;
    private PollingThread m_pollingThread;
}