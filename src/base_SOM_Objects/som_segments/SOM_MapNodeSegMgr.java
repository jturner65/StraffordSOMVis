package base_SOM_Objects.som_segments;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.TreeMap;

import base_SOM_Objects.som_examples.SOMMapNode;
import base_SOM_Objects.som_segments.segmentData.SOM_MapNodeSegmentData;
import base_SOM_Objects.som_segments.segments.SOM_MappedSegment;
import base_UI_Objects.my_procApplet;
import base_Utils_Objects.io.MessageObject;

/**
 * This object will manage a map node's interaction with all of its segments of a particular type
 * There will be one of this object for a particular map node for a particular type of segment
 * @author john
 *
 */
public abstract class SOM_MapNodeSegMgr {
	protected MessageObject msgObj;
	//owning map node
	protected SOMMapNode owner;
	protected TreeMap<Integer, SOM_MapNodeSegmentData> segData;			//segment membership manager of class mapping - key is class/category
	protected TreeMap<Integer, Float> segDataRatio;						//this is the ratio of # of a particular class to the total # of classes/categories mapped to this map node - these should of course sum to 1 (probability)
	protected Float ttlNumMappedInstances;								//total # of training examples mapped to this map node - float to make sure non-int division when consumed

	public SOM_MapNodeSegMgr(SOMMapNode _owner) {
		owner = _owner;
		msgObj = owner.mapMgr.buildMsgObj();
		segData = new TreeMap<Integer, SOM_MapNodeSegmentData>();
		segDataRatio = new TreeMap<Integer, Float>();
	}
	/**
	 * add segment relevant info from passed training example
	 * @param idxs idx of class and/or category - idx 0 is always relevant to specific class, idx 1+ is subordinate idxs 
	 * @param numEx # of examples to add at class
	 * @param segNameStr string template to use for name of constructed segment
	 * @param segDescStr string template to use for description of constructed segment
	 * @return amount added at idxs[0]
	 */
	public abstract Float addSegDataFromTrainingEx(Integer[] idxs, Float numEx, String segNameStr, String segDescStr);	
	/**
	 * copy segment information to this owning node from passed map node - means this seg mgr's owning node has no bmus
	 * @param dist dist this node is from ex
	 * @param otrSegIDCounts similar node ex's particular map of segment ids to counts
	 * @param segNameStr string template to use for name of constructed segment
	 * @param segDescStr string template to use for description of constructed segment
	 */
	@SuppressWarnings("rawtypes")
	public abstract void copySegDataFromBMUMapNode(double dist, TreeMap otrSegIDCounts, String segNameStr, String segDescStr);
		
	/**
	 * specific format depending on instancing class - this is called after all nodes are mapped to owning map node
	 */
	public abstract void clearAllSegData();

	
	public final void setSeg(Integer key, SOM_MappedSegment _seg) {segData.get(key).setSeg(_seg);}
	
	public final SOM_MappedSegment getSegment(Integer key) {
		SOM_MapNodeSegmentData segMgrAtIdx = segData.get(key);
		if(null==segMgrAtIdx) {return null;}			//does not have seg weight at this feature index
		return segMgrAtIdx.getSegment();
	}
	public final int getSegClrAsInt(Integer key) {
		SOM_MapNodeSegmentData segMgrAtIdx = segData.get(key);
		if(null==segMgrAtIdx) {return 0;}			//does not have seg weight at this feature index	
		return segMgrAtIdx.getSegClrAsInt();
	}	
		
	//for passed -Category label (not cat idx)- give this node's probability
	public final float getSegProb(Integer key) {
		Float prob = segDataRatio.get(key);
		if(null==prob) {return 0.0f;}
		return prob;
	}
	public final Set<Integer> getSegIDs(){	return segData.keySet();}
	
	@SuppressWarnings("rawtypes")
	public abstract TreeMap getMappedCounts();	
	/**
	 * get count of examples at segID - is float to facilitate calculations
	 * @param segID
	 * @return
	 */
	public abstract Float getMappedCountAtSeg(Integer segID);
	
	public final TreeMap<Integer, Float> getSegDataRatio(){return segDataRatio;}
	//float so that we can divide with ints more easily
	public final Float getTtlNumMappedInstances() { return ttlNumMappedInstances;}	
	//return string describing segment probabilities for owning map node
	
	//will return owning node's map location,  probability and segment ID for each segment owner belongs to
	public final String getSegDataDescStringForNode() {
		TreeMap<Float, ArrayList<String>> mapNodeProbs = new TreeMap<Float, ArrayList<String>>(Collections.reverseOrder());
		for(Integer segID : segDataRatio.keySet()) {
			float prob = segDataRatio.get(segID);
			ArrayList<String> valsAtProb = mapNodeProbs.get(prob);
			if(null==valsAtProb) {valsAtProb = new ArrayList<String>();}
			valsAtProb.add(""+segID);
			mapNodeProbs.put(prob, valsAtProb);				
		}	//ttlNumMappedInstances
		String res = "" + owner.mapNodeCoord.toCSVString()+",";
		for (Float prob : mapNodeProbs.keySet()) {
			ArrayList<String> valsAtProb = mapNodeProbs.get(prob);
			int count = Math.round(prob * ttlNumMappedInstances);
			String probString = ""+String.format("%1.7g", prob)+",";
			for(String segStr : valsAtProb) {	res +=probString +count+","+ segStr + ",";}
		}			
		return res;	
	}//getSegDataDescStringForNode
	
	public abstract String getSegDataDescStrForNode_Hdr();
	
	
	//draw class pop segment contribution 
	public void drawMeSegClr(my_procApplet p, Integer cls) {
		SOM_MapNodeSegmentData classMgrAtIdx = segData.get(cls);
		if(null==classMgrAtIdx) {
			System.out.println("drawMeSegClr : Error seg is null!");
			return;}			//does not have class members at stated class
		float prob = segDataRatio.get(cls);
		if(0.0f==prob) {return;}
		classMgrAtIdx.drawMe(p,(int) (235*prob)+20);
	}//drawMeFtrWtSegClr


}//SOM_SegmentManager
