package strafford_SOM_PKG.straff_SOM_Examples.convRawToTrain.events;

import strafford_SOM_PKG.straff_RawDataHandling.raw_data.BaseRawData;
import strafford_SOM_PKG.straff_RawDataHandling.raw_data.OptEvent;
import strafford_SOM_PKG.straff_SOM_Examples.convRawToTrain.JpgJpDataRecord;

public class OptEventRawToTrainData extends StraffEvntRawToTrainData{
	public OptEventRawToTrainData(OptEvent ev) {		super(ev);		addEventDataFromEventObj(ev);}	//put in child ctor in case child-event specific data needed for training		
	public OptEventRawToTrainData(Integer _evIDStr, String _evTypeStr, String _evDateStr, String _evntStr){super(_evIDStr, _evTypeStr, _evDateStr);		addJPG_JPDataFromCSVString(_evntStr);}	//put in child ctor in case child-event specific data needed for training	
	@Override
	public void addEventDataFromEventObj(BaseRawData ev) {
		Integer optValKey = ((OptEvent)ev).getOptType();
		super.addEventDataRecsFromRawData(optValKey, ev, true);
	}
	//opt value in csv record between tags optKeyTag and JPG tag
	private Integer getOptValFromCSVStr(String _csvDataStr) {
		Integer res=  0;
		String[] resAra = _csvDataStr.trim().split("optKey,");
		String[] resAra2 = resAra[1].trim().split(",JPG");		
		res = Integer.parseInt(resAra2[0]);
		return res;
	}//getOptValFromCSVStr	
	@Override
	public void addJPG_JPDataFromCSVString(String _csvDataStr) {
		Integer optValKey = getOptValFromCSVStr(_csvDataStr);
		super.addJPG_JPDataRecsFromCSVStr(optValKey, _csvDataStr);
	}
	//get the output string holding the relevant info for an individual event record of this kind of data
	@Override
	protected String getRecCSVString(JpgJpDataRecord rec) {		return jpgrpStTag+"optKey,"+rec.getOptVal()+","+rec.getCSVString()+jpgrpEndTag;};
}//class OptEventTrainData