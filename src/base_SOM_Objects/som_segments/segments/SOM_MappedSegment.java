package base_SOM_Objects.som_segments.segments;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;

import base_SOM_Objects.*;
import base_SOM_Objects.som_examples.*;
import base_Utils_Objects.*;
import base_Utils_Objects.vectorObjs.Tuple;

/**
 * this class will be used to describe a segment/cluster of the SOM containing  
 * a collection of map nodes that are similar to one another.  This will be used for 
 * recommendations.  Built with scc-like algorithm
 * @author john base 
 */
public abstract class SOM_MappedSegment {
	protected static SOM_MapManager mapMgr;
	//unique identifier
	public final int ID;
	private static int count=0;
	//map nodes making up this segment, keyed by location in map
	protected ConcurrentSkipListMap<Tuple<Integer,Integer>, SOMMapNode> MapNodes;
	//map of map nodes keyed by segment "value", in descending order, with value being array of map nodes sharing that value
	protected ConcurrentSkipListMap<Float,ArrayList<SOMMapNode>> MapNodesByDescValue;
	//color to paint this segment
	protected int[] segClr;
	//color as int value
	protected int segClrAsInt;

	//pass initial node of SCC for this segment
	public SOM_MappedSegment(SOM_MapManager _mapMgr) {
		mapMgr = _mapMgr; ID = count++;  
		segClr = mapMgr.getRndClr(150);
		segClrAsInt = ((segClr[3] & 0xff) << 24) + ((segClr[0] & 0xff) << 16)  + ((segClr[1] & 0xff) << 8) + (segClr[2] & 0xff);
		MapNodes = new ConcurrentSkipListMap<Tuple<Integer,Integer>, SOMMapNode>();
		MapNodesByDescValue = new ConcurrentSkipListMap<Float,ArrayList<SOMMapNode>>(new Comparator<Float>() { @Override public int compare(Float o1, Float o2) {   return o2.compareTo(o1);}});
	}//ctor
	
	//called internally by instancing class
	protected final void addMapNode(SOMMapNode _node) {	MapNodes.put(_node.mapNodeCoord, _node);}	
	public final Collection<SOMMapNode> getAllMapNodes(){return MapNodes.values();}
	
	public final void clearMapNodes() {MapNodes.clear();}	
	public final int[] getSegClr() {return segClr;}
	public final int getSegClrAsInt() {return segClrAsInt;}
	
	/**
	 * determine whether a node belongs in this segment - base it on BMU
	 * @param ex the example to check
	 */
	public abstract boolean doesExampleBelongInSeg(SOMExample ex);
	
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
	
	/**
	 * this will build an arraylist of csv output for each bmu that maps to this segment
	 * @return
	 */
	public ArrayList<String> buildBMUMembership_CSV(){
		ArrayList<String> res = new ArrayList<String>();
		finalizeSegment();
		String tmp = _buildBMUMembership_CSV_Hdr();
		res.add(tmp);
		for(Float segVal : MapNodesByDescValue.keySet()) {
			ArrayList<SOMMapNode> bmusAtVal = MapNodesByDescValue.get(segVal);
			for(SOMMapNode bmu : bmusAtVal) {		res.add(_buildBMUMembership_CSV_Detail(bmu));}			
		}
		return res;		
	}//buildBMUMembership_CSV
	
	/**
	 * Build the descriptive string for the passed map node appropriately for this particular segment (scalar value per map node)
	 * Override this function if some instancing class uses a different format to display a map node's segment membership value/probability
	 * @param _bmu map node member of segment
	 * @return string of appropriate format for CSV output
	 */	
	protected String _buildBMUMembership_CSV_Detail(SOMMapNode _bmu) {	return "" + String.format("%1.7g",getBMUSegmentValue(_bmu)) + ","+getBMUSegmentCount(_bmu) +","+_bmu.mapNodeCoord.toCSVString();}	
	
	/**
	 * for every map node in this segment, build MapNodesByDescValue, a map keyed by segment value of 
	 * particular map node, with the value of the map being an array of map nodes with that segment value, in descending order
	 */
	private final void finalizeSegment() {
		MapNodesByDescValue.clear();
		for (SOMMapNode _bmu : MapNodes.values()) {
			Float bmuVal = getBMUSegmentValue(_bmu);
			ArrayList<SOMMapNode> bmusAtVal = MapNodesByDescValue.get(bmuVal);
			if(null==bmusAtVal) {bmusAtVal = new ArrayList<SOMMapNode>(); MapNodesByDescValue.put(bmuVal,bmusAtVal);}
			bmusAtVal.add(_bmu);
		}		
	}//finalizeSegment	
	
	/**
	 * return bmu's value for this segment
	 * @param _bmu
	 * @return
	 */
	protected abstract Float getBMUSegmentValue(SOMMapNode _bmu);
	/**
	 * return bmu's count of examples for this segment
	 * @param _bmu
	 * @return
	 */
	protected abstract Float getBMUSegmentCount(SOMMapNode _bmu);

	/**
	 * build descriptive string for hdr before bmu output
	 * @return
	 */
	protected abstract String _buildBMUMembership_CSV_Hdr();	
	
	
}//class SOMMapSegment
