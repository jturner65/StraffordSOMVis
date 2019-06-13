package strafford_SOM_PKG.straff_SOM_Examples.prospects;

import java.util.Date;
import java.util.SortedMap;
import java.util.TreeMap;

import base_Utils_Objects.vectorObjs.Tuple;
import strafford_SOM_PKG.straff_SOM_Mapping.Straff_SOMMapManager;

/**
 * This example is purely a customer order - a customer prospect example will actually own 1 or more of these
 * This will reuse much of the functionality currently in the CustProspectExample class - it is as if we are
 * treating each customer order individually.
 * This represents a single order and the events leading up to that order.  The intention is to use the 
 * date decay date from the order date to model the behavior of a prospect leading up to an order.  Then,
 * when matching true prospects and customers to the map, we use the current date to find the folks most likely
 * to convert now for a particular product.
 * 
 * @author john
 */
public class Cust_OneOrderTrainingExample extends CustProspectExample{
	//the customer prospect that owns this training example
	public final CustProspectExample owner;
	//date order corresponding to this record was made
	public final Date orderDate;	
	
	//uses csv string constructor in ProspectExample, but is built mostly in here from data passed by owner
	//_order is order responsible for this training example
	//_dateEventOccurrences is submap of all occurrences on or before this order's date
	public Cust_OneOrderTrainingExample(CustProspectExample _owner, String _passedID, DateEvent_OccurrenceData _order, SortedMap<Date,TreeMap<String,DateEvent_OccurrenceData>> navigableMap) {
		super(_owner.mapMgr, _passedID);
		owner=_owner; 
		//orderOnDateOcc = _order;
		orderDate = _order.evntDate;
		mostRecentOrderCounts = _order.getOccurrenceCounts();
		//build occ structure, as if calling finalizeBuildBeforeFtrCalc
		buildOccStructFromDateEvents(navigableMap);
	}//ctor
	
	//we build the JP_Occurrence structure here, to match the configuration of the standard prospect examples, so that the ftr vector data can be calculated
	//this needs to reproduce the finalizeBuildBeforeFtrCalc call
	private void buildOccStructFromDateEvents(SortedMap<Date, TreeMap<String, DateEvent_OccurrenceData>> _dateEventOccurrences) {
		//now convert all DateEvent_OccurrenceData to JP_Occurences for this object
		//occurrence structures - map keyed by event type of map of 
		JpOccurrences = new TreeMap<String, TreeMap<Integer, JP_OccurrenceData>> ();
		DateEvent_OccurrenceData dateOcc;
		TreeMap<Integer, JP_OccurrenceData> allJPOccsForType;
		TreeMap<Tuple<Integer, Integer>, TreeMap<Integer, Integer>> allOccsOnDate;
		TreeMap<String, DateEvent_OccurrenceData> _allTypesOccsOnDate;
		for(String typeKey : jpOccTypeKeys) {JpOccurrences.put(typeKey, new TreeMap<Integer, JP_OccurrenceData>());}
		for(Date date : _dateEventOccurrences.keySet()) {
			_allTypesOccsOnDate = _dateEventOccurrences.get(date);
			for(int i=0;i<jpOccTypeKeys.length;++i) {
				String typeKey = jpOccTypeKeys[i];
				boolean usesOpt = jpOccMapUseOccData[i];
				dateOcc = _allTypesOccsOnDate.get(typeKey);
				if(dateOcc == null) {continue;}
				allOccsOnDate = dateOcc.getAllOccurrences();
				allJPOccsForType = JpOccurrences.get(typeKey);				
				
				for(Tuple<Integer, Integer> jpgJp : allOccsOnDate.keySet()) {
					Integer jpg = jpgJp.x, jp=jpgJp.y;
					TreeMap<Integer, Integer> valsAtDateForJp = allOccsOnDate.get(jpgJp);
					JP_OccurrenceData jpOcc = allJPOccsForType.get(jp);
					if(jpOcc==null) {jpOcc=new JP_OccurrenceData(jp, jpg, usesOpt); allJPOccsForType.put(jp, jpOcc);}
					jpOcc.addOccurrence(date, valsAtDateForJp);					
				}				
			}
		}
				
		buildJPListsAndSetBadExample();
		//now build feature vector, norm vector, non-zero idxs, etc.
		buildFeatureVector();
		//now post build needs to run
		postFtrVecBuild();
	}//buildOccStructFromDateEvents

	@Override
	protected void buildFeaturesMap() {
		//access calc object for this 	
		if (allProdJPs.size() > 0) {
			clearFtrMap(ftrMapTypeKey);//
			((Straff_SOMMapManager)mapMgr).ftrCalcObj.calcTrainingFtrDataVec(this,allProdJPs, ftrMaps[ftrMapTypeKey],orderDate, JpOccurrences);				
		} else {ftrMaps[ftrMapTypeKey].clear();}
	}//buildFeaturesMap
	
	@Override
	/**
	 *  this will build the comparison feature vector array that is used as the comparison vector in distance measurements
	 *  these kinds of per-order examples should never be used, or needed, to be -mapped- to the SOM, since the SOM calc will provide
	 *  their mappings, and furthermore we never wish to quantify these examples - they don't represent customers or true prospects
	 * @param _ratio : 0 means all base ftrs, 1 means all compValMap for features
	 */
	public final void buildCompFtrVector(float _ratio) {compFtrMaps = ftrMaps;}

	
}//class Cust_1OrderTrainingExample