package base_SOM_Objects.som_utils.segments;


import java.util.TreeMap;

import base_SOM_Objects.SOMMapManager;
import base_SOM_Objects.som_examples.*;

/**
 * this class corresponds to a segment built from categories (collections of similar classes) being present in map nodes
 * @author john
 */
public class SOM_CategorySegment extends SOMMapSegment {
	public final Integer category;
	public SOM_CategorySegment(SOMMapManager _mapMgr, Integer _cat) {
		super(_mapMgr);
		category=_cat;
	}

	/**
	 * determine whether a node belongs in this segment - base it kind of example and whether it has a bmu or not
	 * @param ex the example to check
	 */
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
	public boolean doesMapNodeBelongInSeg(SOMMapNode ex) {
				//return map of jpgs to jps to counts present
		TreeMap<Integer, TreeMap<Integer, Integer>> categoryMap = ex.getMappedCategoryCounts();
		return (ex.getCategorySegment(category)== null) && categoryMap.keySet().contains(category);
	}

	@Override
	protected void setMapNodeSegment(SOMMapNode mapNodeEx) {	mapNodeEx.setCategorySeg(category, this);	}


}//class Straff_JPGroupOrderSegment
