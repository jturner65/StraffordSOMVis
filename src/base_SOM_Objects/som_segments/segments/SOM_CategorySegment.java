package base_SOM_Objects.som_segments.segments;


import java.util.TreeMap;

import base_SOM_Objects.SOM_MapManager;
import base_SOM_Objects.som_examples.*;

/**
 * this class corresponds to a segment built from categories (collections of similar classes) being present in map nodes
 * @author john
 */
public final class SOM_CategorySegment extends SOM_MappedSegment {
	public final Integer category;
	public SOM_CategorySegment(SOM_MapManager _mapMgr, Integer _cat) {
		super(_mapMgr);
		category=_cat;
	}

	/**
	 * determine whether a node belongs in this segment - base it kind of example and whether it has a bmu or not
	 * @param ex the example to check
	 */
	@Override
	public final boolean doesExampleBelongInSeg(SOMExample ex) {
		//get type of example from ex
		ExDataType exType = ex.getType();
		switch (exType) {		
			case Training	: 
			case Testing	: 
			case Product	:
			case Validation	: {
				SOMMapNode bmu = ex.getBmu();
				if(bmu==null) {return false;}			
				return doesMapNodeBelongInSeg(bmu);
			}
			case MapNode	: {		return doesMapNodeBelongInSeg((SOMMapNode) ex);}
			case MouseOver	: {		return false;}
		}//switch
		return false;
	}

	@Override
	public final boolean doesMapNodeBelongInSeg(SOMMapNode ex) {
				//return map of jpgs to jps to counts present
		TreeMap<Integer, TreeMap<Integer, Float>> categoryMap = ex.getMappedCategoryCounts();
		return (ex.getCategorySegment(category)== null) && categoryMap.keySet().contains(category);
	}

	@Override
	protected final void setMapNodeSegment(SOMMapNode mapNodeEx) {	mapNodeEx.setCategorySeg(category, this);	}
	
	/**
	 * return bmu's value for this segment
	 * @param _bmu
	 * @return
	 */
	@Override
	protected final Float getBMUSegmentValue(SOMMapNode _bmu) {	return _bmu.getCategoryProb(category);	}
	/**
	 * return bmu's count of examples for this segment
	 * @param _bmu
	 * @return
	 */
	@Override
	protected Float getBMUSegmentCount(SOMMapNode _bmu) {  return _bmu.getMappedCategoryCountAtSeg(category);}
	

	/**
	 * build descriptive string for hdr before bmu output
	 * @return
	 */
	@Override
	protected final String _buildBMUMembership_CSV_Hdr() {
		String title = mapMgr.getCategorySegmentTitleString(category);
		String csvHdr = "Catgory Probability,Ex Count at BMU,BMU Map Loc";
		return title + "\n" + csvHdr;
	}
	

}//class SOM_CategorySegment
