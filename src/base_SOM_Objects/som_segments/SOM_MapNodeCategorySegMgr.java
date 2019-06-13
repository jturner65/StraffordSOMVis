package base_SOM_Objects.som_segments;

import java.util.TreeMap;

import base_SOM_Objects.som_examples.SOMMapNode;
import base_SOM_Objects.som_segments.segmentData.SOM_MapNodeSegmentData;

public class SOM_MapNodeCategorySegMgr extends SOM_MapNodeSegMgr {
	//this holds category as key, value is classes in that category and counts mapped to the node of that class(subtree)
	//counts are floats to enable us to use weighted counts
	protected TreeMap<Integer, TreeMap<Integer, Float>> mappedCategoryCounts;

	public SOM_MapNodeCategorySegMgr(SOMMapNode _owner) {
		super(_owner);
		//build structure that holds counts of categories mapped to this node (category is a collection of similar classes)
		mappedCategoryCounts = new TreeMap<Integer, TreeMap<Integer, Float>>();
	}

	/**
	 * add segment relevant info from passed training example
	 * @param idxs idx of class and/or category - idx 0 is always relevant to specific class, idx 1+ is subordinate idxs 
	 * @param numClsEx # of examples to add at category for idx[1] (class) - always current # to add (dont add more here)
	 * @param segNameStr string template to use for name of constructed segment
	 * @param segDescStr string template to use for description of constructed segment
	 */
	@Override
	public Float addSegDataFromTrainingEx(Integer[] idxs, Float numClsEx, String segNameStr, String segDescStr) {
		Integer cat = idxs[0], cls = idxs[1];
		TreeMap<Integer, Float> clsCountsAtJpGrp = mappedCategoryCounts.get(cat);
		if(null==clsCountsAtJpGrp) {
			clsCountsAtJpGrp = new TreeMap<Integer, Float>(); 
			//on initial mapping for this jpg, build the jpGroup_SegData object for this jpg
			segData.put(cat, new SOM_MapNodeSegmentData(owner, owner.OID+segNameStr+cat, segDescStr+cat));	
		}
		clsCountsAtJpGrp.put(cls, numClsEx);
		mappedCategoryCounts.put(cat, clsCountsAtJpGrp);
		return numClsEx;
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
	public void copySegDataFromBMUMapNode(double dist, TreeMap otrSegIDCounts, String segNameStr, String segDescStr) {
		TreeMap<Integer, TreeMap<Integer, Float>> otrMappedCategoryCounts = (TreeMap<Integer, TreeMap<Integer, Float>>)otrSegIDCounts;//ex.getMappedCategoryCounts();
		TreeMap<Integer, Float> otrClassCounts,thisClassCounts;
		for(Integer cat : otrMappedCategoryCounts.keySet()) { 
			otrClassCounts = otrMappedCategoryCounts.get(cat);
			thisClassCounts = mappedCategoryCounts.get(cat);
			if(thisClassCounts==null) { 
				thisClassCounts = new TreeMap<Integer, Float>(); 
				//on initial mapping for this jpg, build the jpGroup_SegData object for this jpg
				segData.put(cat, new SOM_MapNodeSegmentData(owner, owner.OID+segNameStr+cat, segDescStr+cat));	
			}
			for(Integer cls : otrClassCounts.keySet()) {thisClassCounts.put(cls, otrClassCounts.get(cls));}
			mappedCategoryCounts.put(cat, thisClassCounts);
		}		
	}//copySegDataFromBMUMapNode

	@Override
	public void clearAllSegData() {
		segDataRatio.clear();
		if(mappedCategoryCounts.size()!=segData.size()) {
			msgObj.dispInfoMessage("SOM_MapNodeCategorySegMgr", "clearAllSegData", "Error : mappedCategoryCounts.size() : " + mappedCategoryCounts.size() + " is not equal to segData.size() : " + segData.size());
		}
		ttlNumMappedInstances = 0.0f;		//should be the same as ttlNumMappedClassInstances - measures same # of orders)
		Float ttlPerCategoryCount = 0.0f;
		TreeMap<Integer, Float> ttlPerCategoryCountsMap = new TreeMap<Integer, Float>();
		for(Integer category : mappedCategoryCounts.keySet()) {
			TreeMap<Integer, Float> classCountsPresent = mappedCategoryCounts.get(category);
			ttlPerCategoryCount = 0.0f;			
			for(Float count : classCountsPresent.values()) {//aggregate counts of all classes seen for this category
				ttlPerCategoryCount += count;
				ttlNumMappedInstances += count;	
			}
			ttlPerCategoryCountsMap.put(category, ttlPerCategoryCount);//set total count per category
		}		
		//compute weighting for each category - proportion of this category's # of classes against total count of classes across all categories
		for(Integer category : segData.keySet()) {
			segData.get(category).clearSeg();	
			segDataRatio.put(category, ttlPerCategoryCountsMap.get(category)/ttlNumMappedInstances);
		}	
	}//clearAllSegData
	@Override
	public final String getSegDataDescStrForNode_Hdr() {
		return "Map Node Loc,Probability,Count,Category";
	}

	@Override
	public TreeMap<Integer, TreeMap<Integer, Float>> getMappedCounts() {return mappedCategoryCounts;}
	/**
	 * get count of examples at segID - is float to facilitate calculations
	 * @param segID
	 * @return
	 */
	@Override
	public Float getMappedCountAtSeg(Integer segID) {
		// TODO Auto-generated method stub
		return (float) mappedCategoryCounts.get(segID).size();
	}

}//SOM_CategorySegMgr
