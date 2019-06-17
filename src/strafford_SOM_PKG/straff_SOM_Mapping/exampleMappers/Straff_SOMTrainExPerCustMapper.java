package strafford_SOM_PKG.straff_SOM_Mapping.exampleMappers;

import java.util.ArrayList;

import base_SOM_Objects.SOM_MapManager;
import base_SOM_Objects.som_examples.ExDataType;
import base_SOM_Objects.som_examples.SOMExample;
import strafford_SOM_PKG.straff_SOM_Examples.prospects.CustProspectExample;
import strafford_SOM_PKG.straff_SOM_Mapping.exampleMappers.base.Straff_SOMCustPrspctMapper_Base;

/**
 * Object to manage strafford-specific example mapping for customers, treating them each individually as a single training record
 * 
 *  @author john
 */
public class Straff_SOMTrainExPerCustMapper extends Straff_SOMCustPrspctMapper_Base {

	public Straff_SOMTrainExPerCustMapper(SOM_MapManager _mapMgr, String _exName, String _longExampleName,boolean _shouldValidate) {super(_mapMgr, _exName, _longExampleName, _shouldValidate);}
	
	@Override
	protected final void reset_Priv() {
		mapMgr.resetTrainDataAras();
	}//reset_Priv	
	
	//this treats every customer's total past behavior as a single training example 
	@Override
	protected final void validateAndAddExToArray(ArrayList<SOMExample> tmpList, SOMExample ex) {	if(!ex.isBadExample()) {tmpList.add(ex);}}//validateAndAddEx	//
	@Override
	//add example from map to array without validation
	protected final SOMExample[] noValidateBuildExampleArray() {		return (CustProspectExample[])(exampleMap.values().toArray(new CustProspectExample[0]));		}	

	@Override
	//after example array has been built, and specific funcitonality for these types of examples
	protected final void buildExampleArrayEnd_Priv(boolean validate) {}

	/**
	 * return customer prospect example array - this will an array of customerProspect records always
	 * this is here so that if per-order training data is generated via an instance of 
	 * Straff_SOMCustPrspctPerOrderMapper class this function will still work to retrieve original customerProspect examples                                                                       
	 * @return array of customerProspect examples
	 */
	@Override
	public final CustProspectExample[] getCustProspectExamples() {
		if((null==SOMexampleArray) ||(SOMexampleArray.length==0)) {	buildExampleArray();}
		return (CustProspectExample[]) SOMexampleArray;
	}
	
}//Straff_SOMCustPrspctMapper
