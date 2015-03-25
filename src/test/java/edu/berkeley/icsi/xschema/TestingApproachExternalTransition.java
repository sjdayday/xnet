package edu.berkeley.icsi.xschema;

import javax.json.Json;
import javax.json.JsonObject;

import uk.ac.imperial.pipe.models.petrinet.AbstractTransitionJsonParameters;

public class TestingApproachExternalTransition extends
		AbstractTransitionJsonParameters {

	@Override
	public void fire() {
		JsonObject jsonObject = getParameters(); 
		int num = jsonObject.getInt("num");
		JsonObject newJson = Json.createObjectBuilder().add("num", ++num).build(); 
		updateParameters(newJson);
	}
}
