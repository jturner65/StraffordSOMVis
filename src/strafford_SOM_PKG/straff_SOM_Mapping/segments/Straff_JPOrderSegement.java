package strafford_SOM_PKG.straff_SOM_Mapping.segments;


import java.util.TreeMap;

import base_SOM_Objects.SOMMapManager;
import base_SOM_Objects.som_examples.*;
import base_SOM_Objects.som_utils.segments.SOMMapSegment;
import strafford_SOM_PKG.straff_SOM_Examples.Straff_SOMMapNode;

/**
 * this class corresponds to a segment built from orders orders being present in map nodes used to train map being present with specific JP - this jp must be a valid product jp
 * @author john
 */
public class Straff_JPOrderSegement extends SOMMapSegment {
	public final Integer jp;

	public Straff_JPOrderSegement(SOMMapManager _mapMgr, Integer _jp) {	
		super(_mapMgr);
		jp=_jp;		
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
	}//doesExampleBelongInSeg
	
	@Override
	public boolean doesMapNodeBelongInSeg(SOMMapNode ex) {
		Straff_SOMMapNode mapNode = (Straff_SOMMapNode)ex;
		TreeMap<Integer, Integer> jpMap = mapNode.getMappedJPCounts();
		//System.out.println("JP : " + jp + " JPMap for SOMMapNode : " + ex.OID +  " : " + jpMap);
		return (mapNode.getJpSegment(jp)== null) && jpMap.keySet().contains(jp);
	}

	@Override
	protected void setMapNodeSegment(SOMMapNode mapNodeEx) {	((Straff_SOMMapNode)mapNodeEx).setJpSeg(jp, this);	}

	
}//class Straff_JPOrderSegement
