package finalProject;
/**
* Defines packet for the server side
*
* ENTS 640 
* Final Project - Group 10
*/


public class ServerPacket extends Packet {
	private int ackNumber;
	
	//constructor
	public ServerPacket (int type, int ack) {
		super(type);
		if (!isValidType(type)) {
			throw new IllegalArgumentException("Type provided is not valid for server packets.");
		}
		ackNumber = ack;
	}
	
	//setter method
	public void setAck(int ack) {
		ackNumber = ack;
	}

	//getter method
	public int getAck() {
		return ackNumber;
	}
	
	//returns packet in byte array
	public byte[] getByte() {
		byte[] arr = new byte[5];
		arr[0] = (byte) this.getType();
		arr[1] = (byte) (ackNumber >> 8);
		arr[2] = (byte) (ackNumber);
		arr[3] = 0;
		arr[4] = 0;
		return arr;
	}		
	//checks if packet type is valid
	public boolean isValidType(int type) {
		if (type == 0xaa || type == 0xcc) {
			return true;
		}
		return false;
	}
}
