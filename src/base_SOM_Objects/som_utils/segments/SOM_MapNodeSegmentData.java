package base_SOM_Objects.som_utils.segments;

import base_SOM_Objects.som_examples.*;
import base_UI_Objects.my_procApplet;

/**
 * this object manages the segment mapping for a single map node - different types of mappings will have different segment data objects
 * This is mainly a struct to hold the data and an interface to render the segment images
 * @author john
 *
 */
public class SOM_MapNodeSegmentData {
	//owning map node
	protected SOMMapNode ownr;
	//dimensions of display box for the owning map node
	protected float[] dispBoxDims;
	//name of this segment data structure
	public final String name;
	//type of segment this data is related to
	public final String type;
	//segment used by this segment data
	protected SOMMapSegment seg; 
	//segment color
	protected int[] segClr;
	//segment color as integer
	protected int segClrAsInt;

	public SOM_MapNodeSegmentData(SOMMapNode _ownr, String _name, String _type) {ownr=_ownr;name=_name; type=_type; dispBoxDims = ownr.getDispBoxDims();clearSeg();}//ctor
	
	//provides default values for colors if no segument is defined
	public void clearSeg() {		seg = null;segClr = new int[4]; segClrAsInt = 0x0;}	
	
	//called by segment itself
	public void setSeg(SOMMapSegment _seg) {
		seg=_seg;
		segClr = _seg.getSegClr();
		segClrAsInt = _seg.getSegClrAsInt();	
	}
	
	public SOMMapSegment getSegment() {return seg;}
	public int getSegClrAsInt() {return segClrAsInt;}
	
	//draw owning node's contribution to this segment
	public void drawMe(my_procApplet p) {	drawMe(p,segClr[3]);}
	public void drawMe(my_procApplet p, int _alpha) {
		p.pushMatrix();p.pushStyle();
		p.setFill(segClr, _alpha);
		p.noStroke();
		p.rect(dispBoxDims);		
		p.popStyle();p.popMatrix();	
	}//drawMeClrRect
	
}//class SOM_MapNodeSegmentData
