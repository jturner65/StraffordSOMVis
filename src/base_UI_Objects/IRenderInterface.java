/**
 * 
 */
package base_UI_Objects;

import base_Utils_Objects.vectorObjs.myPoint;
import base_Utils_Objects.vectorObjs.myPointf;
import base_Utils_Objects.vectorObjs.myVector;
import base_Utils_Objects.vectorObjs.myVectorf;

/**
 * These are the functions that are expected to be found in a rendering class for proper rendering 
 * @author john
 *
 */
public interface IRenderInterface {
	//vars in interface are automatically static/final
	public double 
		halfPi = .5*Math.PI,
		twoPi = 2.0*Math.PI,
		threeQtrPI = .75 * Math.PI,
		sqrt2 = Math.sqrt(2.0),
		invSqrt2 = .5 * sqrt2,
		sqrt3 = Math.sqrt(3.0),
		invSqrt3 = 1.0/sqrt3;

	public float 
		halfPi_f = (float) halfPi,
		twoPi_f = (float) twoPi,
		threeQtrPI_f = (float) threeQtrPI,
		sqrt2_f = (float) sqrt2,
		invSqrt2_f = (float) invSqrt2,
		sqrt3_f = (float) sqrt3,
		invSqrt3_f = (float) invSqrt3;	

	public int gui_rnd = -1;
	public int gui_Black 	= 0;
	public int gui_White 	= 1;	
	public int gui_Gray 	= 2;
	
	public int gui_Red 	= 3;
	public int gui_Blue 	= 4;
	public int gui_Green 	= 5;
	public int gui_Yellow 	= 6;
	public int gui_Cyan 	= 7;
	public int gui_Magenta = 8;
	
	public int gui_LightRed = 9;
	public int gui_LightBlue = 10;
	public int gui_LightGreen = 11;
	public int gui_LightYellow = 12;
	public int gui_LightCyan = 13;
	public int gui_LightMagenta = 14;
	public int gui_LightGray = 15;
	
	public int gui_DarkCyan = 16;
	public int gui_DarkYellow = 17;
	public int gui_DarkGreen = 18;
	public int gui_DarkBlue = 19;
	public int gui_DarkRed = 20;
	public int gui_DarkGray = 21;
	public int gui_DarkMagenta = 22;
	
	public int gui_FaintGray = 23;
	public int gui_FaintRed = 24;
	public int gui_FaintBlue = 25;
	public int gui_FaintGreen = 26;
	public int gui_FaintYellow = 27;
	public int gui_FaintCyan = 28;
	public int gui_FaintMagenta = 29;
	
	public int gui_TransBlack = 30;
	public int gui_TransGray = 31;
	public int gui_TransMagenta = 32;	
	public int gui_TransLtGray = 33;
	public int gui_TransRed = 34;
	public int gui_TransBlue = 35;
	public int gui_TransGreen = 36;
	public int gui_TransYellow = 37;
	public int gui_TransCyan = 38;	
	public int gui_TransWhite = 39;	
	public int gui_OffWhite = 40;
	
	///////////////////////
	// required methods
	
	/////////////////////
	// transformation stack and transformations
	
	/**
	 * push gl transformation matrix onto trans stack
	 */
	public void pushMatrix();
	/**
	 * pop gl transformation matrix off of trans stack
	 */
	public void popMatrix();
	/**
	 * push current style params onto "style stack" (save current settings)
	 */	
	public void pushStyle();
	/**
	 * pop current style params from "style stack" (restore/overwrite with last saved settings)
	 */		
	public void popStyle();
	
	/**
	 * translate by the given values
	 * @param x,y,z
	 */
	public void translate(float x, float y, float z);
	public void translate(double x, double y, double z);	
	public void translate(myPoint p);
	public void translate(myPointf p);
	
	/**
	 * rotate by given theta around specified axis 
	 * @param thet
	 * @param axis must be normalized
	 */
	public void rotate(float thet, myPoint axis);
	/**
	 * rotate by given theta around specified axis 
	 * @param thet
	 * @param axis must be normalized
	 */
	public void rotate(float thet, myPointf axis);
	/**
	 * rotate by given theta around specified axis 
	 * @param thet
	 * @param x,y,z must be normalized
	 */
	public void rotate(float thet, double x, double y, double z);
	
	/**
	 * set fill color
	 * @param clr 1st 3 values denot integer color vals
	 * @param alpha 
	 */
	public void setFill(int[] clr, int alpha);
	/**
	 * set stroke color
	 * @param clr 1st 3 values denot integer color vals
	 * @param alpha 
	 */
	public void setStroke(int[] clr, int alpha);
	
	/**
	 * tell current drawing cycle that there should be no stroke 
	 */
	public void noStroke();
	
	/**
	 * set stroke weight
	 */
	public void setStrokeWt(float stW);

	 /**
	  * changes normal for smooth shading
	  * @param V
	  */
	public void gl_normal(myVector V);          
	/**
	 * vertex for shading or drawing
	 * @param P
	 */
	public void gl_vertex(myPoint P) ;   
	/**
	 * changes normal for smooth shading - float
	 * @param V
	 */
	public void gl_normal(myVectorf V) ;   
	/**
	 * vertex for shading or drawing - float
	 * @param P
	 */
	public void gl_vertex(myPointf P);
	/**
	 * set the detail of drawn spheres using passed detail value
	 * @param det
	 */
	public void setSphereDetail(int det);
	/**
	 * draw a sphere of specified radius
	 * @param rad 
	 */
	public void drawSphere(float rad);
	
	/**
	 * draw a 2 d ellipse 
	 * @param a 4 element array : x,y,x rad, y rad
	 */
	public void drawEllipse(float[] a);
	/**
	 * draw a circle centered at P with specified radius r in plane proscribed by passed axes using n number of points
	 * @param P center
	 * @param r radius
	 * @param I x axis
	 * @param J y axis
	 * @param n # of points to use
	 */
	public void drawCircle(myPoint P, float r, myVector I, myVector J, int n);
	
	/**
	 * draw a rectangle in 2D using the passed values as x,y,w,h
	 * @param a 4 element array : x,y,w,h
	 */
	public void drawRect(float[] a);
	
	/**
	 * this will translate the passed box dimensions to keep them on the screen
	 * using p as start point and rectDims[2] and rectDims[3] as width and height
	 * @param P starting point
	 * @param rectDims box dimensions 
	 */
	public void transToStayOnScreen(myPointf P, float[] rectDims);
	
	/**
	 * Draw Axes at ctr point
	 * @param len length of axis
	 * @param stW stroke weight (line thickness)
	 * @param ctr ctr point to draw axes
	 * @param alpha alpha value for how dark/faint axes should be
	 * @param centered whether axis should be centered at ctr or just in positive direction at ctr
	 */
	public void drawAxes(double len, float stW, myPoint ctr, int alpha, boolean centered);
	
	

	
	//TODO put all functions commonly used from myDispWindow and its inheritors in here to support different rendering engines

}//IRenderInterface
