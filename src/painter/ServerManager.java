/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package painter;

import java.awt.Color;
import java.awt.Point;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author S
 */
public class ServerManager implements Runnable {

    private Socket socket;
    private ObjectOutputStream oos;
    private ObjectInputStream ois;
    private String clientIP;
    private int clientRow;
    private Thread thread;
    private Painter paint;
    private boolean clinetIsActive;
    private boolean clinetIsUpFront;
    private boolean clinetIsFullScreen;

    ServerBroadcast serverBroadCase;
    private volatile boolean threadRunning = true;

    public ServerManager(Socket s, Painter paint, ServerBroadcast server) {
        socket = s;
        this.paint = paint;
        serverBroadCase = server;
    }

    @Override
    public void run() {
        try {
            openStreams();
        } catch (IOException e) {
            System.out.println("Error connecting with the client " + e);
        }
    }

    Runnable receiveFromClient = new Runnable() {
        @Override
        public void run() {
            while (threadRunning) {
                receiveFromClient();
            }
        }
    };

    public void killRunningReceivingThread() {
        threadRunning = false;
    }

    private void receiveFromClient() {
        if (socket != null && ois != null) {
            try {
                Object obj = ois.readObject();

                if (obj instanceof StateInfo) {

                    StateInfo stateInfo = (StateInfo) obj;
                    String stateTyp = stateInfo.getType();
                    boolean state = stateInfo.getState();
                    String studentDeviceName = Utility.extractClientAddressSegments(stateInfo.getInfo())[0];

                    switch (stateTyp) {
                        case "isUpFront": {
                            clinetIsUpFront = state;
                            serverBroadCase.updateCell(state, clientRow, 3);
                            paint.IndicateWhetherStudentsWindowsAreNotOK();
                            break;
                        }
                        case "isActive": {
                            clinetIsActive = state;
                            serverBroadCase.updateCell(state, clientRow, 2);
                            paint.IndicateWhetherStudentsWindowsAreNotOK();
                            break;
                        }
                        case "isFullScreen": {
                            clinetIsFullScreen = state;
                            serverBroadCase.updateCell(state, clientRow, 4);
                            paint.IndicateWhetherStudentsWindowsAreNotOK();
                            break;
                        }
                        case "ClientClose": {
                            clientExited();
                            paint.IndicateWhetherStudentsWindowsAreNotOK();
                            break;
                        }
                    }
                } else if (obj instanceof String) {
                    String msgFromClinet = (String) obj;
                    if (msgFromClinet.startsWith("address")) {

                        String fullClientAddress = msgFromClinet.replaceFirst("address", "");
                        int row = serverBroadCase.addClientAddress(fullClientAddress);

                        if (row > -1) {
                            //If added OK
                            clientIP = fullClientAddress;
                            clientRow = row;
                        } else {
                            //If not added due to duplicate client instance
                            clientDuplicate();
                        }
                    }
                }
            } catch (IOException | ClassNotFoundException ex) {
                Logger.getLogger(ServerManager.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public boolean isClinetIsActive() {
        return clinetIsActive;
    }

    public boolean isClinetIsUpFront() {
        return clinetIsUpFront;
    }

    public boolean isClinetIsFullScreen() {
        return clinetIsFullScreen;
    }

    public void clientExited() {
        closeStreams();
        serverBroadCase.removeServerInstance_dueTo_ClientExit(this);
    }

    public void clientDuplicate() {
        closeStreams();
        serverBroadCase.removeServerInstance_dueTo_ClientDuplicate(this);
    }

    private void openStreams() throws IOException {
        if (socket != null) {
            oos = new ObjectOutputStream(socket.getOutputStream());
            ois = new ObjectInputStream(socket.getInputStream());
            send_welcome_toClient();
            send_live_background(paint.getBackground());

            if (paint.getDrawingsList().size() > 0) {
                sendPreMadeDrawingsData(paint.getDrawingsList());
            } else {
                send_newPage_command("newDrawList");
            }
            if (paint.getCurveList_redo().size() > 0) {
                send_initial_live_redo_drawData(new DrawData_forRedo(paint.getCurveList_redo()));
            } else {
                send_newPage_command("newRedoList");
            }
            //
            paint.IndicateWhetherStudentsWindowsAreNotOK();
            thread = new Thread(receiveFromClient);
            thread.start();
        }
    }

    private void send_welcome_toClient() {
        writeObject("welcome");
    }

    private void send_live_background(Color color) {
        writeObject(color);
    }

    public void sendPreMadeDrawingsData(ArrayList<Object> listOfDrawingsData) {
        if ((listOfDrawingsData != null) && (listOfDrawingsData.size() > 0)) {
            writeObject(listOfDrawingsData);
        }
    }

    private void send_initial_live_redo_drawData(DrawData_forRedo drawdata_redo) {
        writeObject(drawdata_redo);
    }

    public void send_drawData_mousePressed(Object drawData) {
        writeObject(drawData);
    }

    public void send_point(Point point) {
        writeObject(point);
    }

    public void send_mouseReleased(Object mouseReleased_Data) {
        writeObject(mouseReleased_Data);
    }

    public void send_newPage_command(String newType) {
        writeObject(newType);
    }

    public void send_undo_command() {
        writeObject("undo");
    }

    public void send_redo_command() {
        writeObject("redo");
    }

    public void send_background(Color background) {
        writeObject(background);
    }

    public void send_maximize_command(String maximize) {
        writeObject(maximize);
    }

    private void writeObject(Object obj) {
        if (socket != null && oos != null) {
            try {
                oos.reset();
                oos.writeObject(obj);
                oos.flush();
            } catch (IOException ex) {
                clientExited();
                Logger.getLogger(ServerManager.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public String getClientIP() {
        if (clientIP == null) {
            return "";
        }
        return clientIP;
    }

    public int getClientRow() {
        return clientRow;
    }

    void closeStreams() {
        try {
            if (oos != null) {
                oos.close();
            }
            if (ois != null) {
                ois.close();
            }
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ex) {
            Logger.getLogger(ServerManager.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

}
