/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package painter;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.*;
import java.awt.event.*;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.*;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;
import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.*;
import javax.swing.JColorChooser;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFormattedTextField;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.*;
import javax.swing.text.DefaultFormatter;

public class Painter extends JPanel {

    ServerBroadcast serverBroadCast; //initialized at ServerBroadcast
    JFrame frame;

    DialogClientsControl dialog;
    int height;
    int width;

    double stroke;

    JSpinner spinner;
    JButton btn_clients_control;

    private JFileChooser filechooser;
    private ArrayList<Object> drawObjects;
    private ArrayList<Object> drawObjects_redo;

    private Color currentColor;

    private File editFile;        // The file that is being edited, if any.

    private JFileChooser fileDialog;   // The dialog box for all open/save commands.

    private static final Color CURVES_COLOR = Color.BLUE;
    private static final Color TEMP_CURVE_COLOR = Color.PINK;

    public Painter(ServerBroadcast server) {

        height = 600;
        width = 600;
        stroke = 10f;

        serverBroadCast = server;

        drawObjects = new ArrayList<>();
        drawObjects_redo = new ArrayList<>();
        currentColor = Color.BLACK;
        setBackground(Color.white);

        SpinnerNumberModel model = new SpinnerNumberModel(stroke, 1, 100, 0.1);
        spinner = new JSpinner(model);

        //The following 4 lines is to enable invoking the changelisener at the same time of typing...
        JComponent comp = spinner.getEditor();
        JFormattedTextField field = (JFormattedTextField) comp.getComponent(0);
        DefaultFormatter formatter = (DefaultFormatter) field.getFormatter();
        formatter.setCommitsOnValidEdit(true);
        //
        spinner.setPreferredSize(new Dimension(45, spinner.getPreferredSize().height));
        spinner.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                stroke = ((double) spinner.getValue());
            }
        });

        btn_clients_control = new JButton("Monitor");
        btn_clients_control.addActionListener(action_btn_clients_control);

        // setResizable(false);
        MouseHandler listener = new MouseHandler();
        this.addMouseListener(listener);
        this.addMouseMotionListener(listener);

        //////
    }


    private class MouseHandler extends MouseAdapter implements MouseMotionListener {

        CurveData curveData;
        PointData pointData;
        boolean wasDragging;

        @Override
        public void mousePressed(MouseEvent e) {

            //Set this press point
            Point point = new Point(e.getX(), e.getY());

            //Set up pointData
            pointData = new PointData(point);
            pointData.setColor(currentColor);
            pointData.setDiameter((float) stroke);
            drawObjects.add(pointData);

            //Set up CurveData
            curveData = new CurveData();
            curveData.setStroke(stroke);
            curveData.setColor(currentColor);
            curveData.setPointsList(new ArrayList<>());
            curveData.getPointsList().add(point);
            drawObjects.add(curveData);

            serverBroadCast.broadcast_mousePressed(curveData);
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            wasDragging = true;

            //Remove pointData, because it is a curve.
            drawObjects.remove(pointData);
            pointData = null;

            // Building the curve points
            Point point = new Point(e.getX(), e.getY());
            curveData.getPointsList().add(point);
            serverBroadCast.broadcast_mouseDragged(point);
            repaint();
        }

        @Override
        public void mouseReleased(MouseEvent e) {

            if (wasDragging) {
                if (curveData.getPointsList().size() < 1) {
                    drawObjects.remove(curveData);
                }
                serverBroadCast.broadcast_mouseReleased("mouseReleased");
            } else {
                // If no dragging, then it is a point, and not a curve.
                drawObjects.remove(curveData);
                repaint(); //repaint for the pointData
                serverBroadCast.broadcast_mouseReleased(pointData);
            }

            pointData = null;
            curveData = null;
            wasDragging = false;

        }
    }

    public ArrayList<Object> getCurveList() {
        return this.drawObjects;
    }

    public ArrayList<Object> getCurveList_redo() {
        return this.drawObjects_redo;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();

        for (Object drawObj : drawObjects) {
            if (drawObj instanceof CurveData) {
                drawCurve(g2, (CurveData) drawObj);
            } else if (drawObj instanceof PointData) {
                drawPoint(g2, (PointData) drawObj);
            }
        }
        g2.dispose();
    }

    private void drawCurve(Graphics2D g2, CurveData curve) {

        g2.setColor(curve.getColor());
        g2.setStroke(getStroke(curve.getStroke()));

        for (int i = 1; i < curve.getPointsList().size(); i++) {
            int x1 = curve.getPointsList().get(i - 1).x;
            int y1 = curve.getPointsList().get(i - 1).y;
            int x2 = curve.getPointsList().get(i).x;
            int y2 = curve.getPointsList().get(i).y;

            g2.drawLine(x1, y1, x2, y2);
        }
    }

    private void drawPoint(Graphics2D g2, PointData point) {

        g2.setColor(point.getColor());

        int diameter = (int) point.getDiameter() + 3;
        int x = point.getPoint().x - diameter / 2;
        int y = point.getPoint().y - diameter / 2;

        g2.fillOval(x, y, diameter, diameter);

    }

    private Stroke getStroke(float stroke) {
        return new BasicStroke(stroke, BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND);
    }

    private void undoPaint() {
        if (drawObjects.size() > 0) {
            drawObjects_redo.add(drawObjects.remove(drawObjects.size() - 1));
            serverBroadCast.broadcast_undo_command();
            repaint();  // Redraw without the curve that has been removed.
        }
    }

    private void redoPaint() {
        if (drawObjects_redo.size() > 0) {
            drawObjects.add(drawObjects_redo.remove(drawObjects_redo.size() - 1));
            serverBroadCast.broadcast_redo_command();
            repaint();  // Redraw without the curve that has been removed.
        }
    }

    private void newPage() {
        drawObjects.clear();
        drawObjects_redo.clear();
        serverBroadCast.broadcast_newPage_command();
        repaint();
    }

    Action action_undo = new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
            undoPaint();
        }
    };

    Action action_redo = new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
            redoPaint();
        }
    };

    Action action_newPage = new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
            newPage();
        }
    };

    Action action_btn_clients_control = new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    if (dialog != null) {
                        dialog.setVisible(true);
                    } else {
                        dialog = new DialogClientsControl(frame, "Users monitor", serverBroadCast);
                    }
                }
            });
        }
    };

    private void set_background(Color color) {
        setBackground(color);
        serverBroadCast.broadcast_background(color);
    }

    public JMenuBar createMenuBar() {

        /* Create the menu bar object */
        JMenuBar menuBar = new JMenuBar();


        /* Create the menus and add them to the menu bar. */
        JMenu fileMenue = new JMenu("File");
        JMenu menuControl = new JMenu("Control");
        JMenu menuColor = new JMenu("Color");
        JMenu menu_bgColor = new JMenu("BackgroundColor");

        menuBar.add(fileMenue);
        menuBar.add(menuControl);
        menuBar.add(menuColor);
        menuBar.add(menu_bgColor);
        menuBar.add(btn_clients_control);
        menuBar.add(spinner);

        /* Add commands to the "Control" menu.  It contains an Undo
             * command that will remove the most recently drawn curve
             * from the list of curves; a "Clear" command that removes
             * all the curves that have been drawn; and a "Use Symmetry"
             * checkbox that determines whether symmetry should be used.
         */
        JMenuItem newVommand = new JMenuItem("New");
        fileMenue.add(newVommand);
        newVommand.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                newPage();
            }
        });

        JMenuItem saveTextCommand = new JMenuItem("Save (text format)");
        fileMenue.add(saveTextCommand);
        saveTextCommand.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                doSaveText();
            }
        });

        JMenuItem openTextCommand = new JMenuItem("Open (text format)");
        fileMenue.add(openTextCommand);
        openTextCommand.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                doOpenText();
            }
        });

        JMenuItem saveBinaryCommand = new JMenuItem("Save (binary format)");
        fileMenue.add(saveBinaryCommand);
        saveBinaryCommand.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                doSaveAsBinary();
            }
        });

        JMenuItem openBinaryCommand = new JMenuItem("Open (binary format)");
        fileMenue.add(openBinaryCommand);
        openBinaryCommand.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                doOpenAsBinary();
            }
        });

        JMenuItem saveImageCommand = new JMenuItem("Save Image");
        fileMenue.add(saveImageCommand);
        saveImageCommand.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                doSaveImage();
            }
        });

        JMenuItem undo = new JMenuItem("Undo");

        menuControl.add(undo);
        undo.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                undoPaint();
            }
        });

        /* Add commands to the "Color" menu.  The menu contains commands for
             * setting the current drawing color.  When the user chooses one of these
             * commands, it has no immediate effect on the drawing.  It justs sets
             * the color that will be used for future drawing.
         */
        menuColor.add(makeColorMenuItem("Black", Color.BLACK));
        menuColor.add(makeColorMenuItem("White", Color.WHITE));
        menuColor.add(makeColorMenuItem("Red", Color.RED));
        menuColor.add(makeColorMenuItem("Green", Color.GREEN));
        menuColor.add(makeColorMenuItem("Blue", Color.BLUE));
        menuColor.add(makeColorMenuItem("Cyan", Color.CYAN));
        menuColor.add(makeColorMenuItem("Magenta", Color.MAGENTA));
        menuColor.add(makeColorMenuItem("Yellow", Color.YELLOW));
        JMenuItem customColor = new JMenuItem("Custom...");
        menuColor.add(customColor);
        customColor.addActionListener(new ActionListener() {
            // The "Custom..." color command lets the user select the current
            // drawing color using a JColorChoice dialog.
            public void actionPerformed(ActionEvent evt) {
                Color c = JColorChooser.showDialog(Painter.this,
                        "Select Drawing Color", currentColor);
                if (c != null) {
                    currentColor = c;
                }
            }
        });

        /* Add commands to the "BackgroundColor" menu.  The menu contains commands
             * for setting the background color of the panel.  When the user chooses
             * one of these commands, the panel is immediately redrawn with the new
             * background color.  Any curves that have been drawn are still there.
         */
        menu_bgColor.add(makeBgColorMenuItem("Chalkboard", new Color(121, 183, 125)));
        menu_bgColor.add(makeBgColorMenuItem("Black", Color.BLACK));
        menu_bgColor.add(makeBgColorMenuItem("White", Color.WHITE));
        menu_bgColor.add(makeBgColorMenuItem("Red", Color.RED));
        menu_bgColor.add(makeBgColorMenuItem("Green", Color.GREEN));
        menu_bgColor.add(makeBgColorMenuItem("Blue", Color.BLUE));
        menu_bgColor.add(makeBgColorMenuItem("Cyan", Color.CYAN));
        menu_bgColor.add(makeBgColorMenuItem("Magenta", Color.MAGENTA));
        menu_bgColor.add(makeBgColorMenuItem("Yellow", Color.YELLOW));
        JMenuItem customBgColor = new JMenuItem("Custom...");
        menu_bgColor.add(customBgColor);

        customBgColor.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                Color c = JColorChooser.showDialog(Painter.this,
                        "Select Background Color", getBackground());
                if (c != null) {
                    set_background(c);
                }
            }
        });

        /* Return the menu bar that has been constructed. */
        return menuBar;

    } // end createMenuBar

    /**
     * This utility method is used to create a JMenuItem that sets the current
     * drawing color.
     *
     * @param command the text that will appear in the menu
     * @param color the drawing color that is selected by this command. (Note
     * that this parameter is "final" for a technical reason: This is a
     * requirement for a local variable that is used in an anonymous inner
     * class.)
     * @return the JMenuItem that has been created.
     */
    private JMenuItem makeBgColorMenuItem(String command, final Color color) {
        JMenuItem item = new JMenuItem(command);
        item.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                set_background(color);
            }
        });
        return item;
    }

    /**
     * This utility method is used to create a JMenuItem that sets the
     * background color of the panel.
     *
     * @param command the text that will appear in the menu
     * @param color the background color that is selected by this command.
     * @return the JMenuItem that has been created.
     */
    private JMenuItem makeColorMenuItem(String command, final Color color) {
        JMenuItem item = new JMenuItem(command);
        item.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                currentColor = color;
            }
        });
        return item;
    }

    private void doSaveText() {
        if (filechooser == null) {
            filechooser = new JFileChooser();
        }
        File selectedFile;

        filechooser.setDialogTitle("Select File to be Saved");
        int option = filechooser.showSaveDialog(this);
        if (option != JFileChooser.APPROVE_OPTION) {
            return;
        }
        selectedFile = filechooser.getSelectedFile();
        if (selectedFile.exists()) {
            int response = JOptionPane.showConfirmDialog(this,
                    "The file \"" + selectedFile.getName()
                    + "\" already exists.\nDo you want to replace it?",
                    "Confirm Save",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (response != JOptionPane.YES_OPTION) {
                return;  // User does not want to replace the file.
            }
        }

        PrintWriter out;
        try {
            FileWriter stream = new FileWriter(selectedFile);
            out = new PrintWriter(stream);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Cannot open a stream to the file " + "\"" + selectedFile + "\"" + " for writing\n" + e);
            return;

        }
        try {
            out.println("PainterSaver 0.1");
            out.println(" Background " + getBackground().getRed() + " " + getBackground().getGreen() + " " + getBackground().getBlue());
//            for (CurveData curve : drawObjects) {
//                out.println("startcurve");
//                out.println(" color " + curve.getColor().getRed() + " " + curve.getColor().getGreen() + " " + curve.getColor().getBlue());
//                out.println(" stroke " + curve.getStroke());
//                for (Point point : curve.getPointsList()) {
//                    out.println("  point " + point.x + " " + point.y);
//                }
//                out.println("endcurve");
//            }
            out.flush();
            out.close();

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Cannot write data to the file " + "\"" + selectedFile + "\"" + "\n" + e);
            return;
        }
    }

    private void doOpenText() {
        if (filechooser == null) {
            filechooser = new JFileChooser();
        }
        File selectedFile = null;

        filechooser.setDialogTitle("Open a File");
        int option = filechooser.showOpenDialog(this);
        if (option != JFileChooser.APPROVE_OPTION) {
            return;
        }

        selectedFile = filechooser.getSelectedFile();

        Scanner scanner;
        try {
            BufferedReader reader = new BufferedReader(new FileReader(selectedFile));
            scanner = new Scanner(reader);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Cannot open a stream to the file " + "\"" + selectedFile + "\"" + " for reading\n" + e);
            return;
        }

        try {
            String programName = scanner.next();
            if (!programName.equalsIgnoreCase("PainterSaver")) {
                JOptionPane.showMessageDialog(this,
                        "Sorry, This is not a PainterSaver File:\n");
            }
            double version = scanner.nextDouble();
            if (version > 0.1) {
                JOptionPane.showMessageDialog(this,
                        "Sorry, This File requires a higher version of PainterSaver File:\n");
            }
            String background = scanner.next();
            if (background.equalsIgnoreCase("Background")) {
                int r = scanner.nextInt();
                int g = scanner.nextInt();
                int b = scanner.nextInt();

                setBackground(new Color(r, g, b));
            }

            drawObjects = new ArrayList<>();

            while (scanner.hasNext()) {
                String item = scanner.next();

                if (item.equalsIgnoreCase("startcurve")) {
                    CurveData currentCurve = new CurveData();
                    currentCurve.setPointsList(new ArrayList<>());
                    item = scanner.next();
                    while (!item.equalsIgnoreCase("endcurve")) {

                        if (item.equalsIgnoreCase("point")) {
                            int x = scanner.nextInt();
                            int y = scanner.nextInt();
                            Point point = new Point(x, y);
                            currentCurve.getPointsList().add(point);
                        } else if (item.equalsIgnoreCase("color")) {
                            int r = scanner.nextInt();
                            int g = scanner.nextInt();
                            int b = scanner.nextInt();
                            currentCurve.setColor(new Color(r, g, b));
                        } else if (item.equalsIgnoreCase("stroke")) {
                            currentCurve.setStroke(scanner.nextInt());
                        }
                        item = scanner.next();
                    }
                    drawObjects.add(currentCurve);
                }
            }
            scanner.close();
            // f.setTitle(selectedFile.getName());
            repaint();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Cannot read data from the file " + "\"" + selectedFile + "\"" + "\n" + e);
            return;
        }

    }

    /**
     * Save the user's image to a file in binary form as serialized objects,
     * using an ObjectOutputStream. Files created by this method can be read
     * back into the program using the doOpenAsBinary() method.
     */
    private void doSaveAsBinary() {
        if (fileDialog == null) {
            fileDialog = new JFileChooser();
        }
        File selectedFile;  //Initially selected file name in the dialog.
        if (editFile == null) {
            selectedFile = new File("sketchData.binary");
        } else {
            selectedFile = new File(editFile.getName());
        }
        fileDialog.setSelectedFile(selectedFile);
        fileDialog.setDialogTitle("Select File to be Saved");
        int option = fileDialog.showSaveDialog(this);
        if (option != JFileChooser.APPROVE_OPTION) {
            return;  // User canceled or clicked the dialog's close box.
        }
        selectedFile = fileDialog.getSelectedFile();
        if (selectedFile.exists()) {  // Ask the user whether to replace the file.
            int response = JOptionPane.showConfirmDialog(this,
                    "The file \"" + selectedFile.getName()
                    + "\" already exists.\nDo you want to replace it?",
                    "Confirm Save",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (response != JOptionPane.YES_OPTION) {
                return;  // User does not want to replace the file.
            }
        }
        ObjectOutputStream out;
        try {
            FileOutputStream stream = new FileOutputStream(selectedFile);
            out = new ObjectOutputStream(stream);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Sorry, but an error occurred while trying to open the file:\n" + e);
            return;
        }
        try {
            out.writeObject(getBackground());
            out.writeInt(drawObjects.size());
//            for (CurveData curve : drawObjects) {
//                out.writeObject(curve);
//            }
            out.close();
            editFile = selectedFile;
            //  f.setTitle("SimplePaint: " + editFile.getName());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Sorry, but an error occurred while trying to write the text:\n" + e);
        }
    }

    /**
     * Read image data from a file into the drawing area. The format of the file
     * must be the same as that used in the doSaveAsBinary() method.
     */
    private void doOpenAsBinary() {
        if (fileDialog == null) {
            fileDialog = new JFileChooser();
        }
        fileDialog.setDialogTitle("Select File to be Opened");
        fileDialog.setSelectedFile(null);  // No file is initially selected.
        int option = fileDialog.showOpenDialog(this);
        if (option != JFileChooser.APPROVE_OPTION) {
            return;  // User canceled or clicked the dialog's close box.
        }
        File selectedFile = fileDialog.getSelectedFile();
        ObjectInputStream in;
        try {
            FileInputStream stream = new FileInputStream(selectedFile);
            in = new ObjectInputStream(stream);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Sorry, but an error occurred while trying to open the file:\n" + e);
            return;
        }
        try {
            Color newBackgroundColor = (Color) in.readObject();
            int curveCount = in.readInt();
            ArrayList<CurveData> newCurves = new ArrayList<CurveData>();
            for (int i = 0; i < curveCount; i++) {
                newCurves.add((CurveData) in.readObject());
            }
            in.close();
//            drawObjects = newCurves;
            setBackground(newBackgroundColor);
            repaint();
            editFile = selectedFile;
            //   setTitle("SimplePaint: " + editFile.getName());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Sorry, but an error occurred while trying to read the data:\n" + e);
        }
    }

    /**
     * Saves the user's sketch as an image file in PNG format.
     */
    private void doSaveImage() {
        if (fileDialog == null) {
            fileDialog = new JFileChooser();
        }
        fileDialog.setSelectedFile(new File("sketch.png"));
        fileDialog.setDialogTitle("Select File to be Saved");
        int option = fileDialog.showSaveDialog(this);
        if (option != JFileChooser.APPROVE_OPTION) {
            return;  // User canceled or clicked the dialog's close box.
        }
        File selectedFile = fileDialog.getSelectedFile();
        if (selectedFile.exists()) {  // Ask the user whether to replace the file.
            int response = JOptionPane.showConfirmDialog(this,
                    "The file \"" + selectedFile.getName()
                    + "\" already exists.\nDo you want to replace it?",
                    "Confirm Save",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (response != JOptionPane.YES_OPTION) {
                return;  // User does not want to replace the file.
            }
        }
        try {
            BufferedImage image;  // A copy of the sketch will be drawn here.
            image = new BufferedImage(600, 600, BufferedImage.TYPE_INT_RGB);
            Graphics g = image.getGraphics();  // For drawing onto the image.
            paintComponent(g);
            g.dispose();
            boolean hasPNG = ImageIO.write(image, "PNG", selectedFile);
            if (!hasPNG) {
                throw new Exception("PNG format not available.");
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Sorry, but an error occurred while trying to write the image:\n" + e);
        }
    }

    public void seFrametTitle(int clients) {
        String title = "Server board";
        if (clients > 0) {
            if (clients == 1) {
                title = "Server board -  " + clients + " client connected";
            } else {
                title = "Server board -  " + clients + " clients connected";
            }
        } else {
            title = "Server board -  No clients connected";
        }
        this.frame.setTitle(title);
    }

    public void createPainterGUI(Painter painter) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {

                frame = new JFrame();
                seFrametTitle(0);
                Toolkit tk = Toolkit.getDefaultToolkit();
                int xSize = ((int) tk.getScreenSize().getWidth() - 250);
                int ySize = ((int) tk.getScreenSize().getHeight() - 200);
                frame.setSize(xSize, ySize);
                frame.setLocationRelativeTo(null);
                frame.setContentPane(painter);

                frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, KeyEvent.CTRL_DOWN_MASK), "undo");
                frame.getRootPane().getActionMap().put("undo", painter.action_undo);

                frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, KeyEvent.CTRL_DOWN_MASK), "redo");
                frame.getRootPane().getActionMap().put("redo", painter.action_redo);

                frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_N, KeyEvent.CTRL_DOWN_MASK), "newPage");
                frame.getRootPane().getActionMap().put("newPage", painter.action_newPage);

                frame.setJMenuBar(painter.createMenuBar());
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.setVisible(true);
            }
        });
    }
}