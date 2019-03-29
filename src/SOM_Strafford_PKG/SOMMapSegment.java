package SOM_Strafford_PKG;

import java.util.*;

/**
 * this class will be used to describe a segment/cluster of the SOM containing  
 * a collection of map nodes that are similar to one another.  This will be used for 
 * recommendations.  Built with scc-like algorithm
 * @author john
 */
public class SOMMapSegment {
	protected static StraffSOMMapManager mapData;
	//unique identifier
	public final int ID;
	private static int count=0;
	//threshold of u-dist for nodes to be considered similar. (0..1.0) (not including bounds) and be included in segment
	protected float thresh;
	//map nodes making up this segment, keyed by location in map
	private TreeMap<Tuple<Integer,Integer>, SOMMapNode> MapNodes;
	//color to paint this segment
	private int[] segClr;
	//color as int value
	private int segClrAsInt;
	
	//pass initial node of SCC for this segment
	public SOMMapSegment(StraffSOMMapManager _mapData, float _thresh) {
		mapData = _mapData; ID = count++; thresh = _thresh; 
		segClr = mapData.getRndClr(150);
		segClrAsInt = ((segClr[3] & 0xff) << 24) + ((segClr[0] & 0xff) << 16)  + ((segClr[1] & 0xff) << 8) + (segClr[2] & 0xff);
		MapNodes = new TreeMap<Tuple<Integer,Integer>, SOMMapNode>();
	}//ctor
	
	public void addNode(SOMMapNode _node) {	MapNodes.put(_node.mapNodeCoord, _node);}
	public void clearNodes() {MapNodes.clear();}
	
	public int[] getSegClr() {return segClr;}
	public int getSegClrAsInt() {return segClrAsInt;}
	
	

}//class SOMMapSegment
