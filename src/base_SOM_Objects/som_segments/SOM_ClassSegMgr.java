package base_SOM_Objects.som_segments;

import java.util.TreeMap;

import base_SOM_Objects.som_examples.SOMExample;
import base_SOM_Objects.som_examples.SOMMapNode;
import base_SOM_Objects.som_segments.segmentData.SOM_MapNodeSegmentData;
/**
 * This class manages all of a single map node's class-type segment functionality.
 * A class-type segment means the segment is built upon degrees of membership to
 * a particular shared class by the examples that map to the owning map node.
 * 
 * @author john
 *
 */
public class SOM_ClassSegMgr extends SOM_SegmentManager {
	//this holds classes and count of all training examples with this class mapped to this node
	//counts are floats to enable us to use weighted counts
	protected TreeMap<Integer, Float> mappedClassCounts;

	public SOM_ClassSegMgr(SOMMapNode _owner) {
		super(_owner);
		//build structure that holds counts of classes mapped to this node
		mappedClassCounts = new TreeMap<Integer, Float>();
	}
	
	/**
	 * add segment relevant info from passed training example
	 * @param idxs idx of class and/or category - idx 0 is always relevant to specific class, idx 1+ is subordinate idxs 
	 * @param numEx # of examples to add at class
	 * @param segNameStr string template to use for name of constructed segment
	 * @param segDescStr string template to use for description of constructed segment
	 */
	@Override
	public Float addSegDataFromTrainingEx(Integer[] idxs, Float numClsEx, String segNameStr, String segDescStr) {
		Integer cls = idxs[0];
		Float newCount = mappedClassCounts.get(cls);
		//for each cls id
		if(null==newCount) {
			//on initial mapping for this cls, build the jp_SegData object for this cls
			newCount = 0.0f;
			segData.put(cls, new SOM_MapNodeSegmentData(owner, owner.OID+segNameStr+cls, segDescStr+cls));
		}
		newCount +=numClsEx;
		mappedClassCounts.put(cls, newCount);
		return newCount;
	}//addSegDataFromTrainingEx	
	
	/**
	 * copy segment information to this owning node from passed map node - means this seg mgr's owning node has no bmus
	 * @param dist dist this node is from ex
	 * @param ex closest map node with examples that consider it bmu
	 * @param segNameStr string template to use for name of constructed segment
	 * @param segDescStr string template to use for description of constructed segment
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override 
	//public void copySegDataFromBMUMapNode(double dist, SOMMapNode ex, String segNameStr, String segDescStr) {
	public void copySegDataFromBMUMapNode(double dist, TreeMap otrSegIDCounts,  String segNameStr, String segDescStr) {
		TreeMap<Integer, Float> otrMappedClassCounts = (TreeMap<Integer, Float>)otrSegIDCounts;//ex.getMappedClassCounts();
		for(Integer cls : otrMappedClassCounts.keySet()) {			
			mappedClassCounts.put(cls, otrMappedClassCounts.get(cls));	
			segData.put(cls, new SOM_MapNodeSegmentData(owner, owner.OID+segNameStr+cls, segDescStr+cls));
		}
	}//copySegDataFromBMUMapNode

	@Override
	public void clearAllSegData() {
		segDataRatio.clear();
		ttlNumMappedInstances = 0.0f;
		//aggregate total count of all classes seen by this node
		if(mappedClassCounts.size()!=segData.size()) {
			msgObj.dispInfoMessage("SOM_ClassSegMgr", "clearAllSegData", "Error : mappedClassCounts.size() : " + mappedClassCounts.size() + " != segData.size() : " + segData.size());
		}
		for(Float count : mappedClassCounts.values()) {ttlNumMappedInstances += count;}
		for(Integer cls : segData.keySet()) {
			segData.get(cls).clearSeg();			//clear each class's segment manager
			segDataRatio.put(cls, mappedClassCounts.get(cls)/ttlNumMappedInstances);
		}	
//		if((compLoc.x==this.mapNodeCoord.x) && (compLoc.y==this.mapNodeCoord.y)) {mapMgr.getMsgObj().dispInfoMessage("SOMMapNode", "clearClassSeg","47,8 Info :  mappedClassCounts.size() : " + mappedClassCounts.size() + " | class_SegData.size() : " + class_SegData.size()+ " | class_SegDataRatio.size() : " + class_SegDataRatio.size());}
		
	}//clearAllSegData()

	@Override
	public TreeMap<Integer, Float> getMappedCounts() {	return mappedClassCounts;}

}//SOM_ClassSegMgr
