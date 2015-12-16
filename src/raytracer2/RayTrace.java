package raytracer2;

import java.applet.Applet;
import java.awt.Color;
import java.awt.Event;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StreamTokenizer;
import java.net.URL;
import java.util.Vector;

import javax.swing.JFrame;


//The following Applet demonstrates a simple ray tracer with a
//mouse-based painting interface for the impatient and Mac owners
public class RayTrace extends JFrame implements Runnable {
    
    final static int CHUNKSIZE = 100;
    BufferedImage screen;
    Graphics gc;
    Vector objectList;
    Vector lightList;
    Surface currentSurface;

    Vector3D eye, lookat, up;
    Vector3D Du, Dv, Vp;
    float fov;

    Color background;

    int width, height;

    public void init(boolean parsefile) {
        
        width = getWidth();
        height = getHeight();
//        screen = createImage(width, height);
        screen = new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
        gc = screen.getGraphics();
        gc.setColor(getBackground());
        gc.fillRect(0, 0, width, height);

        fov = 30;               // default horizonal field of view

        // Initialize various lists
        objectList = new Vector(CHUNKSIZE, CHUNKSIZE);
        lightList = new Vector(CHUNKSIZE, CHUNKSIZE);
        currentSurface = new Surface(0.8f, 0.2f, 0.9f, 0.2f, 0.4f, 0.4f, 10.0f, 0f, 0f, 1f);

        // Parse the scene file
        if (parsefile) {
            InputStream is = null;
            try {
                is = new FileInputStream("src/raytracer2/scene.txt");
                ReadInput(is);
            } catch (IOException e) {
                System.err.println("Error reading file");
                System.exit(-1);
            }
        }

        // Initialize more defaults if they weren't specified
        if (eye == null) eye = new Vector3D(0, 0, 10);
        if (lookat == null) lookat = new Vector3D(0, 0, 0);
        if (up  == null) up = new Vector3D(0, 1, 0);
        if (background == null) background = new Color(0,0,0);

        // Compute viewing matrix that maps a
        // screen coordinate to a ray direction
        Vector3D look = new Vector3D(lookat.x - eye.x, lookat.y - eye.y, lookat.z - eye.z);
        Du = Vector3D.normalize(look.cross(up));
        Dv = Vector3D.normalize(look.cross(Du));
        float fl = (float)(width / (2*Math.tan((0.5*fov)*Math.PI/180)));
        Vp = Vector3D.normalize(look);
        Vp.x = Vp.x*fl - 0.5f*(width*Du.x + height*Dv.x);
        Vp.y = Vp.y*fl - 0.5f*(width*Du.y + height*Dv.y);
        Vp.z = Vp.z*fl - 0.5f*(width*Du.z + height*Dv.z);
    }


    double getNumber(StreamTokenizer st) throws IOException {
        if (st.nextToken() != StreamTokenizer.TT_NUMBER) {
            System.err.println("ERROR: number expected in line "+st.lineno());
            throw new IOException(st.toString());
        }
        return st.nval;
    }

    void ReadInput(InputStream is) throws IOException {
        Reader readur = new BufferedReader(new InputStreamReader(is));
        StreamTokenizer st = new StreamTokenizer(readur);
        st.commentChar('#');
        boolean flag = true;
        out: while (flag) {
            switch (st.nextToken()) {
            default:
                continue;
            case StreamTokenizer.TT_WORD:
                if (st.sval.equals("sphere")) {
                    Vector3D v = new Vector3D((float) getNumber(st), (float) getNumber(st), (float) getNumber(st));
                    float r = (float) getNumber(st);
                    objectList.addElement(new Sphere(currentSurface, v, r));
                } else if (st.sval.equals("end"))  {
                    break out;
                } else if (st.sval.equals("eye")) {
                    eye = new Vector3D((float) getNumber(st), (float) getNumber(st), (float) getNumber(st));
//                    System.out.println("eye " + eye);
                } else if (st.sval.equals("lookat")) {
                    lookat = new Vector3D((float) getNumber(st), (float) getNumber(st), (float) getNumber(st));
//                    System.out.println("lookat " + lookat);
                } else if (st.sval.equals("up")) {
                    up = new Vector3D((float) getNumber(st), (float) getNumber(st), (float) getNumber(st));
//                    System.out.println("up " + up);
                } else if (st.sval.equals("fov")) {
                    fov = (float) getNumber(st);
//                    System.out.println("fov " + fov);
                } else if (st.sval.equals("background")) {
                    background = new Color((float) getNumber(st), (float) getNumber(st), (float) getNumber(st));
//                    System.out.println("background " + background);
                } else if (st.sval.equals("light")) {
                    float r = (float) getNumber(st);
                    float g = (float) getNumber(st);
                    float b = (float) getNumber(st);
                    if (st.nextToken() != StreamTokenizer.TT_WORD) {
                        throw new IOException(st.toString());
                    }
                    if (st.sval.equals("ambient")) {
                        lightList.addElement(new Light(Light.AMBIENT, null, r, g, b));
//                        System.out.println("ambient light");
                    } else if (st.sval.equals("directional")) {
                        Vector3D v = new Vector3D((float) getNumber(st), (float) getNumber(st), (float) getNumber(st));
                        lightList.addElement(new Light(Light.DIRECTIONAL, v, r, g, b));
//                        System.out.println("directional light");
                    } else if (st.sval.equals("point")) {
                        Vector3D v = new Vector3D((float) getNumber(st), (float) getNumber(st), (float) getNumber(st));
                        lightList.addElement(new Light(Light.POINT, v, r, g, b));
//                        System.out.println("point light");
                    } else {
                        System.err.println("ERROR: in line "+st.lineno()+" at "+st.sval);
                        throw new IOException(st.toString());
                    }
                } else if (st.sval.equals("surf")) {
//                    System.out.println("i'm here!");
                    float r = (float) getNumber(st);
                    float g = (float) getNumber(st);
                    float b = (float) getNumber(st);
                    float ka = (float) getNumber(st);
                    float kd = (float) getNumber(st);
                    float ks = (float) getNumber(st);
                    float ns = (float) getNumber(st);
                    float kr = (float) getNumber(st);
                    float kt = (float) getNumber(st);
                    float index = (float) getNumber(st);
                    currentSurface = new Surface(r, g, b, ka, kd, ks, ns, kr, kt, index);
//                    System.out.println("surface " + currentSurface);
                }
                break;
            
            }
        }
//       System.out.println("ok");
        is.close();
//        if (st.ttype != StreamTokenizer.TT_EOF)
//            throw new IOException(st.toString());
    }

    boolean finished = false;

    public void paint(Graphics g) {
        if (finished)
            g.drawImage(screen, 0, 0, this);
    }

    // this overide avoid the unnecessary clear on each paint()
    public void update(Graphics g) {
        paint(g);
    }


    Thread raytracer;

//    public void start() {
//      if (raytracer == null) {
//          raytracer = new Thread(this);
//          raytracer.start();
//      } else {
//          raytracer.resume();
//      }
//    }
//    
//    public void stop() {
//      if (raytracer != null) {
//          raytracer.suspend();
//      }
//    }
    
    public void coverscreen() {
        Graphics g = getGraphics();
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                gc.setColor(background);
                gc.drawLine(i, j, i, j);  
            }
            g.drawImage(screen, 0, 0, this);        // doing this less often speed things up a bit
        }
        g.drawImage(screen, 0, 0, this);
    }
    
    Vector3D CalculateBezierPoint(float t,
            Vector3D p0, Vector3D p1, Vector3D p2, Vector3D p3)
          {
            float u = 1-t;
            float tt = t*t;
            float uu = u*u;
            float uuu = u*u*u;
            float ttt = t*t*t;
           
            Vector3D p = p0.scalarmult(uuu); //first term
            p = p1.scalarmult(3 * uu * t).add(p); //second term
            p = p2.scalarmult(3 * u * tt).add(p); //third term
            p = p3.scalarmult(ttt).add(p); //fourth term
           
            return p;
          }
    
    public void drawBezierCurve(Vector3D p0, Vector3D p1, Vector3D p2, Vector3D p3, int pointnum) {
        Graphics g = getGraphics();
        gc.setColor(Color.WHITE);
        double increment = 1.0/pointnum;
        for (int i = 0; i <= 1; i += increment) {
            Vector3D point = CalculateBezierPoint(i, p0, p1, p2, p3);
            gc.drawLine((int)point.x, (int)point.y, (int)point.x, (int)point.y);  
        }
        g.drawImage(screen, 0, 0, this);
        finished = true;
        this.update(g);
    }
    
    public void drawCircleArc(Vector3D c, Vector3D s, double f) {
        Graphics g = getGraphics();
        int r = (int) Math.sqrt((s.x-c.x)*(s.x-c.x) + (s.y-c.y)*(s.y-c.y));
        int x = (int) (c.x-r);
        int y = (int) (c.y-r);
        int width = 2*r;
        int height = 2*r;
        int startAngle = (int) (180/Math.PI*Math.atan2((double)(s.y-c.y), (double)(s.x-c.x)));
        int endAngle = (int) (180/Math.PI*(2*Math.PI*f));
        gc.setColor(Color.WHITE);
        gc.drawArc(x, y, width, height, startAngle, endAngle);
        g.drawImage(screen, 0, 0, this);
        finished = true;
        this.update(g);
    }
    
    public void drawBresenhamLine(int x,int y,int x2, int y2, Color color) {
        Graphics g = getGraphics();
        int w = x2 - x ;
        int h = y2 - y ;
        int dx1 = 0, dy1 = 0, dx2 = 0, dy2 = 0 ;
        if (w<0) dx1 = -1 ; else if (w>0) dx1 = 1 ;
        if (h<0) dy1 = -1 ; else if (h>0) dy1 = 1 ;
        if (w<0) dx2 = -1 ; else if (w>0) dx2 = 1 ;
        int longest = Math.abs(w) ;
        int shortest = Math.abs(h) ;
        if (!(longest>shortest)) {
            longest = Math.abs(h) ;
            shortest = Math.abs(w) ;
            if (h<0) dy2 = -1 ; else if (h>0) dy2 = 1 ;
            dx2 = 0 ;            
        }
        int numerator = longest >> 1 ;
        for (int i=0;i<=longest;i++) {
            gc.setColor(color);
            gc.drawLine(x, y, x, y); 
            numerator += shortest ;
            if (!(numerator<longest)) {
                numerator -= longest ;
                x += dx1 ;
                y += dy1 ;
            } else {
                x += dx2 ;
                y += dy2 ;
            }
        }
        g.drawImage(screen, 0, 0, this);
        finished = true;
        this.update(g);
    }

    private void renderPixel(int i, int j) {
        Vector3D dir = new Vector3D(
                i*Du.x + j*Dv.x + Vp.x,
                i*Du.y + j*Dv.y + Vp.y,
                i*Du.z + j*Dv.z + Vp.z);
        Ray ray = new Ray(eye, dir);
        if (ray.trace(objectList)) {
            gc.setColor(ray.Shade(lightList, objectList, background));
        } else {
            gc.setColor(background);
        }
        gc.drawLine(i, j, i, j);        // drawing part
    }

    public void run() {
        Graphics g = getGraphics();
        long time = System.currentTimeMillis();
        for (int j = 0; j < height; j++) {
            System.out.println("Scanline "+j);
            for (int i = 0; i < width; i++) {
                renderPixel(i, j);
            }
            g.drawImage(screen, 0, 0, this);        // doing this less often speed things up a bit
        }
        g.drawImage(screen, 0, 0, this);
        time = System.currentTimeMillis() - time;
        System.out.println("Rendered in "+(time/60000)+":"+((time%60000)*0.001));
        finished = true;
    }


    public boolean mouseDown(Event e, int x, int y) {
        renderPixel(x, y);
        repaint();
        return true;
    }

    public boolean mouseDrag(Event e, int x, int y) {
        renderPixel(x, y);
        repaint();
        return true;
    }

    public boolean mouseUp(Event e, int x, int y) {
        renderPixel(x, y);
        repaint();
        return true;
    }
    
    public static void main(String args[]) {
        
        RayTrace frame = new RayTrace();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(640, 480);
        frame.setVisible(true);
//        frame.init(false);
//        frame.setBackground(Color.BLACK);
//        frame.coverscreen();
//        frame.drawBezierCurve(new Vector3D(20, 20, 0), new Vector3D(20, 200, 0), new Vector3D(400, 200, 0), new Vector3D(400, 20, 0), 100);
        
//        frame.drawCircleArc(new Vector3D(300, 200, 0) , new Vector3D(100, 100, 0), 0.5);
//        frame.drawBresenhamLine(0, 0, 640, 480, Color.WHITE);
        frame.init(true);
        frame.run();
        
    }
}
