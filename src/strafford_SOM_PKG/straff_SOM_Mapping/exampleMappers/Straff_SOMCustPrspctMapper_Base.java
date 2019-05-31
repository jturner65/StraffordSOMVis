package strafford_SOM_PKG.straff_SOM_Mapping.exampleMappers;

import java.util.*;
import java.util.concurrent.Future;

import base_SOM_Objects.SOMMapManager;
import base_SOM_Objects.som_examples.SOMExample;
import base_SOM_Objects.som_fileIO.SOMExCSVDataLoader;
import strafford_SOM_PKG.straff_ProcDataHandling.data_loaders.CustCSVDataLoader;
import strafford_SOM_PKG.straff_SOM_Examples.prospects.CustProspectExample;
import strafford_SOM_PKG.straff_SOM_Mapping.Straff_SOMMapManager;
import strafford_SOM_PKG.straff_Utils.featureCalc.StraffWeightCalc;

/**
 * base class to manage customer prospects - instanced by either per-customer training example manager or per-order training example manager.
 * @author john
 */
public abstract class Straff_SOMCustPrspctMapper_Base extends Straff_SOMProspectMapper {

	public Straff_SOMCustPrspctMapper_Base(SOMMapManager _mapMgr, String _exName, String _longExampleName, boolean _shouldValidate) {		super(_mapMgr, _exName, _longExampleName, _shouldValidate);	}//ctor
	
	private final void dispAllNumOrderCounts() {
		msgObj.dispInfoMessage("Straff_SOMCustPrspctMapper","buildStraffFtrVec_Priv->dispAllNumOrderCounts","# of customers with particular order count : ");
		msgObj.dispInfoMessage("Straff_SOMCustPrspctMapper","buildStraffFtrVec_Priv->dispAllNumOrderCounts","\t# of Unique JPs\t# of Customers with this many Unique Jps");
		int ttlOrders = 0;
		for(Integer numJPs : CustProspectExample.ttlOrderCount.keySet()) {
			Integer[] orderDat = CustProspectExample.ttlOrderCount.get(numJPs);
			msgObj.dispInfoMessage("Straff_SOMCustPrspctMapper","buildStraffFtrVec_Priv->dispAllNumOrderCounts","\t"+numJPs+"\t\t"+orderDat[0]+"\t\t"+orderDat[1]);
			ttlOrders += orderDat[1];
		}
		msgObj.dispInfoMessage("Straff_SOMCustPrspctMapper","buildStraffFtrVec_Priv->dispAllNumOrderCounts","\tTotal # of Orders across all customers : " + ttlOrders);
		// the # of customers considered "bad" after features were built
		msgObj.dispInfoMessage("Straff_SOMCustPrspctMapper","buildStraffFtrVec_Priv->dispAllNumOrderCounts","\tTotal # Of Customers considered 'bad' after features were built : " + CustProspectExample.NumBadExamplesAfterFtrsBuilt + " responsible for " + CustProspectExample.NumBadExampleOrdersAfterFtrsBuilt+" orders.  These examples shouldn't be used to train.");
	}//dispAllNumOrderCounts
	
	@Override
	protected final SOMExample[] castArray(ArrayList<SOMExample> tmpList) {	return (CustProspectExample[])(tmpList.toArray(new CustProspectExample[0]));}
	/**
	 * return customer prospect example array - this will an array of customerProspect records always
	 * this is here so that if per-order training data is generated via an instance of 
	 * Straff_SOMCustPrspctPerOrderMapper class this function will still work to retrieve original customerProspect examples                                                                       
	 * @return array of customerProspect examples
	 */
	public abstract CustProspectExample[] getCustProspectExamples();
	
	/**
	 * code to execute after examples have had ftrs prepared - this calculates feature vectors
	 */
	@Override
	protected final void buildStraffFtrVec_Priv() {
		//reset calc analysis objects before building feature vectors to enable new analytic info to be aggregateds
		CustProspectExample.NumBadExamplesAfterFtrsBuilt = 0;		//reset count of "bad" customer records, as reported by eq calculations (0-value ftr vec) - don't train on these guys
		CustProspectExample.NumBadExampleOrdersAfterFtrsBuilt=0;
			//clear out records of order counts
		CustProspectExample.ttlOrderCount.clear();
		CustProspectExample.ttlBadOrderCount.clear();

		((Straff_SOMMapManager)mapMgr).ftrCalcObj.resetCalcObjs(StraffWeightCalc.custCalcObjIDX);	
		((Straff_SOMMapManager)mapMgr).ftrCalcObj.resetCalcObjs(StraffWeightCalc.trainCalcObjIDX);	//also reset training data calc for order-based training data
		
		//call to buildFeatureVector for all examples
		mapMgr._ftrVecBuild(exampleMap.values(),0,exampleName);	
		
		//manage per-order calculations
		//display order information after features were built
		dispAllNumOrderCounts();
		
		msgObj.dispInfoMessage("StraffSOMMapManager","buildPrspctFtrVecs : " + exampleName + " Examples","Begin Setting Non-Product Jp Eqs training ftr vectors from Customer examples.");
		//these calls to initAllEqsForCustNonTrainCalc and finalizeAllEqsCustForNonTrainCalc manage for each non-product 
		//jp the exemplar ftr vector that most closely described their data - this will then be applied to each true prospect 
		((Straff_SOMMapManager)mapMgr).ftrCalcObj.initAllEqsForCustNonTrainCalc();	
		//build per-non-prod jp ftr vector contribution
		for (SOMExample ex : exampleMap.values()) {		((CustProspectExample) ex).buildNonProdJpFtrVec();}
		
		((Straff_SOMMapManager)mapMgr).ftrCalcObj.finalizeAllEqsCustForNonTrainCalc();	
		msgObj.dispInfoMessage("StraffSOMMapManager","buildPrspctFtrVecs : " + exampleName + " Examples","Finished Setting Non-Product Jp Eqs training ftr vectors from Customer examples.");			
		
		//call to _ftrVecBuild() with _typeOfProc==1 calls postFtrVecBuild for all examples of specified type - strafford data doesn't currently use this functionality so we can comment this call
		//mapMgr._ftrVecBuild(exs, 1, exType);	
		//set calc/calc analysis state as finished
		((Straff_SOMMapManager)mapMgr).ftrCalcObj.finishFtrCalcs(StraffWeightCalc.custCalcObjIDX);	
		((Straff_SOMMapManager)mapMgr).ftrCalcObj.finishFtrCalcs(StraffWeightCalc.trainCalcObjIDX);	
	}//buildFtrVec_Priv()
	
	
	@Override
	//manage multi-threaded loading
	protected final void buildMTLoader(String[] loadSrcFNamePrefixAra, int numPartitions) {
		List<Future<Boolean>> preProcLoadFtrs = new ArrayList<Future<Boolean>>();
		List<SOMExCSVDataLoader> preProcLoaders = new ArrayList<SOMExCSVDataLoader>();
		for (int i=0; i<numPartitions;++i) {	preProcLoaders.add(new CustCSVDataLoader(mapMgr, i, loadSrcFNamePrefixAra[0]+"_"+i+".csv",  exampleName+ " Data file " + i +" of " +numPartitions + " loaded",  exampleName+ " Data File " + i +" of " +numPartitions +" Failed to load", exampleMap));}	
		try {preProcLoadFtrs = th_exec.invokeAll(preProcLoaders);for(Future<Boolean> f: preProcLoadFtrs) { 			f.get(); 		}} catch (Exception e) { e.printStackTrace(); }					
	}//buildMTLoader
	
	@Override
	//manage single threaded loading
	protected final void buildSTLoader(String[] loadSrcFNamePrefixAra, int numPartitions) {
		for (int i=numPartitions-1; i>=0;--i) {
			String dataFile = loadSrcFNamePrefixAra[0]+"_"+i+".csv";
			String[] csvLoadRes = fileIO.loadFileIntoStringAra(dataFile,  exampleName+ " Data file " + i +" of " +numPartitions +" loaded",  exampleName+ " Data File " + i +" of " +numPartitions +" Failed to load");
			//ignore first entry - header
			for (int j=1;j<csvLoadRes.length; ++j) {
				String str = csvLoadRes[j];
				int pos = str.indexOf(',');
				String oid = str.substring(0, pos);
				CustProspectExample ex = new CustProspectExample(mapMgr, oid, str);
				exampleMap.put(oid, ex);			
			}
		}
	}//buildSTLoader
	
	
}//Straff_SOMCustPrspctMapper
