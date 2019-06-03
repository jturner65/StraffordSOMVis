package strafford_SOM_PKG.straff_ProcDataHandling.data_loaders;

import java.util.concurrent.ConcurrentSkipListMap;

import base_SOM_Objects.SOMMapManager;
import base_SOM_Objects.som_examples.SOMExample;
import base_SOM_Objects.som_fileIO.SOMExCSVDataLoader;
import strafford_SOM_PKG.straff_SOM_Examples.prospects.CustProspectExample;

public class CustCSVDataLoader extends SOMExCSVDataLoader{

	public CustCSVDataLoader(SOMMapManager _mapMgr, int _thdIDX, String _fileName, String _yStr, String _nStr,
			ConcurrentSkipListMap<String, SOMExample> _mapToAddTo) {	
		super(_mapMgr, _thdIDX, _fileName, _yStr, _nStr, _mapToAddTo);type="custCSVDataLoader";
	}
	@Override
	protected synchronized SOMExample buildExample(String oid, String str) {return new CustProspectExample(mapMgr, oid, str);}
	
}//custCSVDataLoader