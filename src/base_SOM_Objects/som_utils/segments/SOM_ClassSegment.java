package base_SOM_Objects.som_utils.segments;


import java.util.TreeMap;

import base_SOM_Objects.SOMMapManager;
import base_SOM_Objects.som_examples.*;

/**
 * this class corresponds to a segment built from orders orders being present in map nodes used to train map being present with specific JP - this jp must be a valid product jp
 * @author john
 */
public class SOM_ClassSegment extends SOMMapSegment {
	public final Integer cls;

	public SOM_ClassSegment(SOMMapManager _mapMgr, Integer _class) {	
		super(_mapMgr);
		cls=_class;		
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
		
		TreeMap<Integer, Integer> classMap = ex.getMappedClassCounts();
		//System.out.println("JP : " + jp + " JPMap for SOMMapNode : " + ex.OID +  " : " + jpMap);
		return (ex.getClassSegment(cls)== null) && classMap.keySet().contains(cls);
	}

	@Override
	protected void setMapNodeSegment(SOMMapNode mapNodeEx) {	mapNodeEx.setClassSeg(cls, this);	}

	
}//class Straff_JPOrderSegement
