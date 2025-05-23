package strafford_SOM_PKG.straff_SOM_Mapping.exampleManagers.base;

import java.util.concurrent.ConcurrentSkipListMap;

import base_SOM_Objects.som_managers.SOM_ExampleManager;
import base_SOM_Objects.som_managers.SOM_MapManager;
import strafford_SOM_PKG.straff_Features.Straff_MonitorJpJpgrp;
import strafford_SOM_PKG.straff_SOM_Mapping.Straff_SOMMapManager;
/**
 * class to manage mapping for all examples (prospects and products) in Strafford project
 * @author john
 *
 */
public abstract class Straff_SOMExampleManager extends SOM_ExampleManager  {
	//manage all jps and jpgs seen in project
	public Straff_MonitorJpJpgrp jpJpgrpMon;	

	public Straff_SOMExampleManager(SOM_MapManager _mapMgr, String _exName, String _longExampleName, boolean[] _flagVals) {
		super(_mapMgr,  _exName, _longExampleName, _flagVals);
		jpJpgrpMon = ((Straff_SOMMapManager)mapMgr).jpJpgrpMon;
	}
	
	
	protected final void dbg_dispFtrVecMinMaxs(ConcurrentSkipListMap<Integer, Float> minMap, ConcurrentSkipListMap<Integer, Float> maxMap, String callClass) {	
		msgObj.dispInfoMessage(callClass,"dbg_dispFtrVecMinMaxs","Start JP,Min Value,Max Value.");
		for(Integer idx : minMap.keySet()) {
			Integer jp = jpJpgrpMon.getFtrJpByIdx(idx);
			msgObj.dispInfoMessage(callClass,"dbg_dispFtrVecMinMaxs",""+jp+","+minMap.get(idx)+","+maxMap.get(idx));
		}
		minMap.clear();
		maxMap.clear();
		msgObj.dispInfoMessage(callClass,"dbg_dispFtrVecMinMaxs","Finished JP,Min Value,Max Value.");
	}

	/**
	 * code to execute after examples have had ftrs prepared - this calculates feature vectors
	 */
	protected final void buildFtrVec_Priv() {
		//set here to have most recent, relevant version
		jpJpgrpMon = ((Straff_SOMMapManager)mapMgr).jpJpgrpMon;
		//instance-specific functions for Strafford examples
		buildStraffFtrVec_Priv();		
	}//buildFtrVec_Priv
	protected abstract void buildStraffFtrVec_Priv();	
	
}//class Straff_SOMExampleMapper
