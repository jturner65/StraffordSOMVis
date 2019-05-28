package base_SOM_Objects.som_utils.segments;

import java.util.*;

import base_SOM_Objects.*;
import base_SOM_Objects.som_examples.*;
import base_Utils_Objects.*;

/**
 * this class will be used to describe a segment/cluster of the SOM containing  
 * a collection of map nodes that are similar to one another.  This will be used for 
 * recommendations.  Built with scc-like algorithm
 * @author john base 
 */
public abstract class SOMMapSegment {
	protected static SOMMapManager mapMgr;
	//unique identifier
	public final int ID;
	private static int count=0;
	//map nodes making up this segment, keyed by location in map
	protected TreeMap<Tuple<Integer,Integer>, SOMMapNode> MapNodes;
	//color to paint this segment
	protected int[] segClr;
	//color as int value
	protected int segClrAsInt;
	
	//pass initial node of SCC for this segment
	public SOMMapSegment(SOMMapManager _mapMgr) {
		mapMgr = _mapMgr; ID = count++;  
		segClr = mapMgr.getRndClr(150);
		segClrAsInt = ((segClr[3] & 0xff) << 24) + ((segClr[0] & 0xff) << 16)  + ((segClr[1] & 0xff) << 8) + (segClr[2] & 0xff);
		MapNodes = new TreeMap<Tuple<Integer,Integer>, SOMMapNode>();
	}//ctor
	
	//called internally by instancing class
	protected void addMapNode(SOMMapNode _node) {	MapNodes.put(_node.mapNodeCoord, _node);}	
	public final Collection<SOMMapNode> getAllMapNodes(){return MapNodes.values();}
	
	public void clearMapNodes() {MapNodes.clear();}
	
	public int[] getSegClr() {return segClr;}
	public int getSegClrAsInt() {return segClrAsInt;}
	
	/**
	 * determine whether a node belongs in this segment - base it on BMU
	 * @param ex the example to check
	 */
	public boolean doesExampleBelongInSeg(SOMExample ex) {
		SOMMapNode mapNode = ex.getBmu();
		if(mapNode == null) {return false;}		//if no bmu then example does not belong in any segment
		return doesMapNodeBelongInSeg(mapNode);		
	}
	/**
	 * determine whether a mapnode belongs in this segment
	 * @param ex map node to check
	 */
	public abstract boolean doesMapNodeBelongInSeg(SOMMapNode ex);
	
	/**
	 * Set the passed map node to have this segment as its segment
	 * @param ex map node to set this as a segment
	 */
	protected abstract void setMapNodeSegment(SOMMapNode mapNodeEx);
	
	/**
	 * If map node meets criteria, add it to this segment as well as its neighbors
	 * @param ex map node to add
	 */
	public final void addMapNodeToSegment(SOMMapNode mapNodeEx,TreeMap<Tuple<Integer,Integer>, SOMMapNode> mapNodes) {
		//add passed map node to this segment - expected that appropriate membership has already been verified
		addMapNode(mapNodeEx);
		//set this segment to belong to passed map node
		setMapNodeSegment(mapNodeEx);
		int row = 1, col = 1;//1,1 is this node for neighborhood
		SOMMapNode neighborEx = mapNodes.get(mapNodeEx.neighborMapCoords[row][col+1]);
		if(doesMapNodeBelongInSeg(neighborEx)) {addMapNodeToSegment(neighborEx,mapNodes);}
		neighborEx = mapNodes.get(mapNodeEx.neighborMapCoords[row][col-1]);
		if(doesMapNodeBelongInSeg(neighborEx)) {addMapNodeToSegment(neighborEx,mapNodes);}
		neighborEx = mapNodes.get(mapNodeEx.neighborMapCoords[row+1][col]);
		if(doesMapNodeBelongInSeg(neighborEx)) {addMapNodeToSegment(neighborEx,mapNodes);}
		neighborEx = mapNodes.get(mapNodeEx.neighborMapCoords[row-1][col]);
		if(doesMapNodeBelongInSeg(neighborEx)) {addMapNodeToSegment(neighborEx,mapNodes);}
	}//addMapNodeToSegment

	
}//class SOMMapSegment
