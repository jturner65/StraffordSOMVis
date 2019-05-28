package base_SOM_Objects.som_utils.segments;

import java.util.TreeMap;

import base_SOM_Objects.SOMMapManager;
import base_SOM_Objects.som_examples.*;

/**
 * this class will manage instances of segments built off feature weight - these segments will overlap with one another - there is one segment per ftr idx
 * @author john
 *
 */
public class SOM_FtrWtSegment extends SOMMapSegment {
	public final int ftrIDX;

	public SOM_FtrWtSegment(SOMMapManager _mapMgr, int _ftrIDX) {
		super(_mapMgr);
		ftrIDX = _ftrIDX;
	}
	
	/**
	 * determine whether a mapnode belongs in this segment getFtrWtSegment
	 */
	public boolean doesMapNodeBelongInSeg(SOMMapNode ex) {
		TreeMap<Integer, Float> ftrs = ex.getCurrentFtrMap(SOMMapManager.useNormedDat);
		Float ftrAtIDX = ftrs.get(ftrIDX);
		return ((ex.getFtrWtSegment(ftrIDX)== null) && (ftrAtIDX!=null) && (ftrAtIDX > 0.0f));
	}

	@Override
	protected void setMapNodeSegment(SOMMapNode mapNodeEx) {	mapNodeEx.setFtrWtSeg(ftrIDX, this);	}


}//class SOM_FtrWtSegment
