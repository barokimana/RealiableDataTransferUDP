package finalProject;
/**
* Defines an abstract class for all packet types
*
* ENTS 640
* Final Project - Group 10
*/

public abstract class Packet {
	private int packetType;
	//private int integrityCheck;

	//constructor
	public Packet (int type) {
		packetType = type;
	}

	//get type
	public int getType() {
			return packetType;
	}
	
}

