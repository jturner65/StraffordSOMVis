package base_SOM_Objects;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentSkipListMap;


import base_Utils_Objects.*;

//this class will load the pre-procced csv data into the prospect data structure owned by the SOMMapData object
public abstract class SOMExCSVDataLoader implements Callable<Boolean>{
	public SOMMapManager mapMgr;
	private messageObject msgObj;
	private String fileName, dispYesStr, dispNoStr;
	private int thdIDX;
	private FileIOManager fileIO;
	//ref to map to add to, either prospects or validation records
	private ConcurrentSkipListMap<String, SOMExample> mapToAddTo;
	protected String type;
	public SOMExCSVDataLoader(SOMMapManager _mapMgr, int _thdIDX, String _fileName, String _yStr, String _nStr, ConcurrentSkipListMap<String, SOMExample> _mapToAddTo) {	
		mapMgr=_mapMgr;
		msgObj=mapMgr.buildMsgObj();thdIDX=_thdIDX;fileName=_fileName;dispYesStr=_yStr;dispNoStr=_nStr; 
		mapToAddTo = _mapToAddTo;
		fileIO = new FileIOManager(msgObj,"SOMExCSVDataLoader TH_IDX_"+String.format("%02d", thdIDX));
		type="";
	}//ctor
	
	protected abstract SOMExample buildExample(String oid, String str);
	
	@Override
	public Boolean call() throws Exception {	
		String[] csvLoadRes = fileIO.loadFileIntoStringAra(fileName, dispYesStr, dispNoStr);
		//ignore first entry - header
		for (int j=1;j<csvLoadRes.length; ++j) {
			String str = csvLoadRes[j];
			int pos = str.indexOf(',');
			String oid = str.substring(0, pos);
			SOMExample ex = buildExample(oid, str);//new custProspectExample(mapMgr, oid, str);
			//ProspectExample oldEx = mapMgr.putInProspectMap(ex);//mapMgr.prospectMap.put(ex.OID, ex);	
			SOMExample oldEx = mapToAddTo.put(ex.OID, ex);	//mapMgr.prospectMap.put(ex.OID, ex);	
			if(oldEx != null) {msgObj.dispMessage("SOMExCSVDataLoader", type+": call thd : " +String.format("%02d", thdIDX), "ERROR : "+thdIDX+" : Attempt to add duplicate record to prospectMap w/OID : " + oid, MsgCodes.error2);	}
		}		
		return true;
	}	
}//class SOMExCSVDataLoader
