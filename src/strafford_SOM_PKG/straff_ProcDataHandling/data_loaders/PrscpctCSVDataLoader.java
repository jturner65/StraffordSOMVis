package strafford_SOM_PKG.straff_ProcDataHandling.data_loaders;

import java.util.concurrent.ConcurrentSkipListMap;

import base_SOM_Objects.SOM_MapManager;
import base_SOM_Objects.som_examples.SOM_Example;
import base_SOM_Objects.som_fileIO.SOM_ExCSVDataLoader;
import strafford_SOM_PKG.straff_SOM_Examples.prospects.TrueProspectExample;

public class PrscpctCSVDataLoader extends SOM_ExCSVDataLoader{
	public PrscpctCSVDataLoader(SOM_MapManager _mapMgr, int _thdIDX, String _fileName, String _yStr, String _nStr,
			ConcurrentSkipListMap<String, SOM_Example> _mapToAddTo) {	
		super(_mapMgr, _thdIDX, _fileName, _yStr, _nStr, _mapToAddTo);type="prscpctCSVDataLoader";
	}
	@Override
	protected synchronized SOM_Example buildExample(String oid, String str) {return new TrueProspectExample(mapMgr, oid, str);}
}//class prscpctCSVDataLoader