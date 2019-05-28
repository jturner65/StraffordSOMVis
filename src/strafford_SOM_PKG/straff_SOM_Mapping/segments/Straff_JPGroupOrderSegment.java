package strafford_SOM_PKG.straff_SOM_Mapping.segments;


import java.util.TreeMap;

import base_SOM_Objects.SOMMapManager;
import base_SOM_Objects.som_examples.*;
import base_SOM_Objects.som_utils.segments.SOMMapSegment;
import strafford_SOM_PKG.straff_SOM_Examples.Straff_SOMMapNode;

/**
 * this class corresponds to a segment built from orders being present in map nodes with specific JPgroup - this jpg must be a valid product jpg
 * @author john
 */
public class Straff_JPGroupOrderSegment extends SOMMapSegment {
	public final Integer jpg;
	public Straff_JPGroupOrderSegment(SOMMapManager _mapMgr, Integer _jpg) {
		super(_mapMgr);
		jpg=_jpg;
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
		Straff_SOMMapNode mapNode = (Straff_SOMMapNode)ex;
			//return map of jpgs to jps to counts present
		TreeMap<Integer, TreeMap<Integer, Integer>> jpGroupMap = mapNode.getMappedJPGroupCounts();
		return (mapNode.getJpGroupSegment(jpg)== null) && jpGroupMap.keySet().contains(jpg);
	}

	@Override
	protected void setMapNodeSegment(SOMMapNode mapNodeEx) {	((Straff_SOMMapNode)mapNodeEx).setJpGroupSeg(jpg, this);	}


}//class Straff_JPGroupOrderSegment
