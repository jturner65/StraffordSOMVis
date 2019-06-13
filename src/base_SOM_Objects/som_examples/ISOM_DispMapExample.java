package base_SOM_Objects.som_examples;

import base_UI_Objects.my_procApplet;
import base_Utils_Objects.vectorObjs.myPointf;

/**
 * This interface is a contract for the required functionality to be implemented by a map-driven display example
 * such as by mouse-over
 * @author john
 *
 */
public interface ISOM_DispMapExample {	
	/**
	 * this builds labels for displaying ftr-map mouse-over data - should be called by ctor for ftr map data
	 */
	public void buildMseLbl_Ftrs();
	/**
	 * this builds labels for displaying umatrix mouse-over data - should be called by distance-based ctor
	 */
	public void buildMseLbl_Dists();
	/**
	 * drawing function
	 * @param p : PApplet ref to manage drawing functionality
	 */
	public void drawMeLblMap(my_procApplet p);
	/**
	 * set example's location on map - SOMExample implements this function
	 * @param _pt : location on visual display of map
	 */
	public void setMapLoc(myPointf _pt);
}//interface SOMMap_DispExample
