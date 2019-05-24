package strafford_SOM_PKG.straff_SOM_Examples;

import java.util.*;

import base_SOM_Objects.*;
import base_SOM_Objects.som_examples.*;
import base_Utils_Objects.*;
import strafford_SOM_PKG.straff_RawDataHandling.raw_data.*;
import strafford_SOM_PKG.straff_SOM_Examples.convRawToTrain.events.LinkEventRawToTrainData;
import strafford_SOM_PKG.straff_SOM_Examples.convRawToTrain.events.OptEventRawToTrainData;
import strafford_SOM_PKG.straff_SOM_Examples.convRawToTrain.events.OrderEventRawToTrainData;
import strafford_SOM_PKG.straff_SOM_Examples.convRawToTrain.events.SrcEventRawToTrainData;
import strafford_SOM_PKG.straff_SOM_Examples.convRawToTrain.events.StraffEvntRawToTrainData;
import strafford_SOM_PKG.straff_SOM_Examples.prospects.JP_OccurrenceData;
import strafford_SOM_PKG.straff_SOM_Mapping.*;
import strafford_SOM_PKG.straff_Utils.MonitorJpJpgrp;


/**
 * NOTE : None of the data types in this file are thread safe so do not allow for opportunities for concurrent modification
 * of any instanced object in this file. This decision was made for speed concerns - 
 * concurrency-protected objects have high overhead that proved greater penalty than any gains in 10 execution threads.
 * 
 * All multithreaded access of these objects should be designed such that any individual object is only accessed by 
 * a single thread.
 * 
 * Objects of this class will hold the relevant data acquired from the db to build a datapoint used by the SOM
 * It will take raw data and build the appropriate feature vector, using the appropriate calculation 
 * to weight the various jpgroups/jps appropriately.  The ID of this construct should be such that it 
 * can be uniquely qualified/indexed by it and will either be the ID of a particular prospect in the 
 * prospect database or some other unique identifier if this is representing, say, a target product
 * @author john
 *
 */

public abstract class Straff_SOMExample extends SOMExample{
	//reference to jp-jpg mapping/managing object
	protected static MonitorJpJpgrp jpJpgMon;
	
	//all jps in this example that correspond to actual products.
	//products are used for training vectors - these will be used to build feature vector used by SOM
	protected HashSet<Integer> allProdJPs;
		
	//alternate comparison structure - used in conjunction with ftrVec of chosen format
	//use a map to hold only sparse data of each frmt for feature vector
	//each array element map corresponds to a type of ftr map - ftr, norm and std
	//each map has key == to _jpg_ and value == multiplier
	protected TreeMap<Integer, Float>[] compValFtrDataMaps;
	public float compValFtrDataMapMag = 0.0f;

	//idxs corresponding to types of events
	
	public Straff_SOMExample(SOMMapManager _map, ExDataType _type, String _id) {
		super(_map, _type, _id);
		jpJpgMon = ((Straff_SOMMapManager) mapMgr).jpJpgrpMon;
		allProdJPs = new HashSet<Integer> ();	
		compValFtrDataMaps = new TreeMap[ftrMapTypeKeysAra.length];
		for (int i=0;i<compValFtrDataMaps.length;++i) {			compValFtrDataMaps[i] = new TreeMap<Integer, Float>(); 		}

	}//ctor
	
	public Straff_SOMExample(Straff_SOMExample _otr) {
		super(_otr);	
		allProdJPs = _otr.allProdJPs;		
		compValFtrDataMaps = _otr.compValFtrDataMaps;
	}//copy ctor
	//instead of rebuilding these, just clear them
	protected void clearCompValMaps() {		for (int i=0;i<compValFtrDataMaps.length;++i) {			compValFtrDataMaps[i].clear(); 		}compValFtrDataMapMag = 0.0f;}
	
	@Override
	//this is called after an individual example's features are built
	protected final void _buildFeatureVectorEnd_Priv() {}		
	
	@Override
	//this is a mapping of non-zero source data elements to their idx in the underlying feature vector
	protected void buildAllNonZeroFtrIDXs() {
		allNonZeroFtrIDXs = new ArrayList<Integer>();
		for(Integer jp : allProdJPs) {
			Integer jpIDX = jpJpgMon.getFtrJpToIDX(jp);
			if(jpIDX==null) {msgObj.dispMessage("StraffSOMExample","buildAllNonZeroFtrIDXs","ERROR!  null value in jpJpgMon.getJpToFtrIDX("+jp+")", MsgCodes.error2 ); }
			else {allNonZeroFtrIDXs.add(jpIDX);}
		}
	}//buildAllNonZeroFtrIDXs (was buildAllJPFtrIDXsJPs)
	
	@Override
	//build a string describing what a particular feature value is
	protected String dispFtrVal(TreeMap<Integer, Float> ftrs, Integer i) {
		Float ftr = ftrs.get(i);
		int jp = jpJpgMon.getFtrJpByIdx(i);
		return "jp : " + jp + " | idx : " + i + " | val : " + String.format("%1.4g",  ftr) + " || ";
	}
	
	public final HashSet<Integer> getAllProdJPs(){return allProdJPs;}
	
	
}//class StraffSOMExample





