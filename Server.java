package finalProject;
/**

* Server for data transmission 
*
* ENTS 640 
* Final Project
*/



import java.net.InetAddress;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Arrays;

public class Server {
	private static int NUM_DATA_PACKETS = 0;
	private static int NUM_DATA_PACKETS_RECEIVED = 0;
	private static int NUM_PAYLOAD_BYTES = 0;	
    private static int nextSequenceNum = 0; //initially set to 0

	//check validity of packet
	public static boolean checkIntegrity(byte[] packet) {
		//check integrity 
		byte[] arr = checkSum(packet);
		if (arr[0] != 0 || arr[1] != 0) {
			System.out.println("Packet failed integrity check!");
			return false;
		}
		return true;		
	}
	
	//check sum: returns checksum bytes		
	public static byte[] checkSum(byte[] packet) {
		int word = 0;
		int variable = 0;
		if(packet.length%2!=0) {
			//insert a zero byte between integrity check byte and the rest of the packet 
			//to make the packet of an even length
			byte[] temp = new byte[packet.length+1]; //new byte array with lenght = packet.lenght+1		
			System.arraycopy(packet, 0, temp, 0, packet.length-2); //copy all bytes except those for integrity check
			temp[temp.length-3] = 0; //insert a zero byte
			System.arraycopy(packet, packet.length-2, temp, temp.length-2, 2); //copy integrity check bytes
		
			for(int i=0; i<temp.length; i=i+2) {
				word = temp[i];
				word = (word<<8) | (temp[i+1] & 0x00ff);
				variable = variable^word;
			}
		}
		else {
			for(int i=0; i<packet.length; i=i+2) {
				word = packet[i];
				word = (word<<8) | (packet[i+1] & 0x00ff);
				variable = variable^word;
			}
		}
		return new byte[] { 
				(byte)(variable >> 8),
				(byte)(variable)};
	}
	
	//get unsigned int from 2 bytes
	public static int getInt(byte msb, byte lsb) {
		int word = 0;
		word = msb; 
		word = ((word << 8) & 0x0000ffff) | (lsb & 0x00ff);
		return word;
	}
		
	public static void main(String[] args) throws Exception {
		//create datagram socket 
		DatagramSocket skt = new DatagramSocket(10995);
		
		//create datagram packet to receive INIT packet
		byte[] buf0 = new byte[8000];
		DatagramPacket initDatagram = new DatagramPacket(buf0, 8000);
		
		while(true) {
			System.out.println("Waiting for INIT packet...");
			skt.receive(initDatagram);
			System.out.println("Packet received");
			byte[] initByteArr = Arrays.copyOfRange(initDatagram.getData(), 0, initDatagram.getLength());
			byte type = initByteArr[0];
			
			if (type==0x55 && checkIntegrity(initByteArr)) {
				System.out.println("Received a valid INIT packet");
				//set fields: NUM_DATA_PACKETS and NUM_PAYLOAD_BYTES
				nextSequenceNum = getInt(initByteArr[1], initByteArr[2]) + 1;
				NUM_DATA_PACKETS = getInt(initByteArr[3], initByteArr[4]);
				NUM_PAYLOAD_BYTES = getInt(initByteArr[5], initByteArr[6]);
	
				//create IACK packet
				int ackNum = nextSequenceNum;
				ServerPacket iackPacket = new ServerPacket(0xaa, ackNum);
				byte[] iackByteArr = iackPacket.getByte();
			
				//set checksum bytes
				byte[] checksumBytes = checkSum(iackByteArr);
				iackByteArr[iackByteArr.length-2] = checksumBytes[0];
				iackByteArr[iackByteArr.length-1] = checksumBytes[1];

				//create IACK Datagram
				DatagramPacket iackDatagram = new DatagramPacket(iackByteArr, iackByteArr.length);
				iackDatagram.setAddress(initDatagram.getAddress());
				iackDatagram.setPort(initDatagram.getPort());
				
				System.out.println("Sending an IACK");
				//send IACK over UDP and go to the next state
				skt.send(iackDatagram);
				break;
			}						 		
		}
		
		//create datagram packet to receive INIT packet
		byte[] buf1 = new byte[8000];
		DatagramPacket dataDatagram = new DatagramPacket(buf1, 8000);
		long startTime = 0;
		long endTime = 0;
		while(NUM_DATA_PACKETS_RECEIVED < NUM_DATA_PACKETS) {
			System.out.println("Expecting DATA packets...");
			skt.receive(dataDatagram);
			System.out.println("Packet received");			
			byte[] dataByteArr = Arrays.copyOfRange(dataDatagram.getData(), 0, dataDatagram.getLength());
			
			//check type
			byte type = dataByteArr[0];
			int packetSequenceNum = getInt(dataByteArr[1], dataByteArr[2]);
			//if INIT, send IACK
			if (type == 0x55) {
				if (checkIntegrity(dataByteArr)) {
					System.out.println("Received duplicate INIT packet");
					//create IACK packet
					int ackNum = nextSequenceNum;
					ServerPacket iackPacket = new ServerPacket(0xaa, ackNum);
					byte[] iackByteArr = iackPacket.getByte();
				
					//set checksum bytes
					byte[] checksumBytes = checkSum(iackByteArr);
					iackByteArr[iackByteArr.length-2] = checksumBytes[0];
					iackByteArr[iackByteArr.length-1] = checksumBytes[1];
				
					//create IACK Datagram
					DatagramPacket iackDatagram = new DatagramPacket(iackByteArr, iackByteArr.length);
					iackDatagram.setAddress(dataDatagram.getAddress());
					iackDatagram.setPort(dataDatagram.getPort());
				
					System.out.println("Resending IACK packet");	
					//send IACK over UDP
					skt.send(iackDatagram);
				}
			}		
			
			else if (type == 0x33) {
			//if packet is data, passes integrity check and has correct seq num
				if (checkIntegrity(dataByteArr) && packetSequenceNum == nextSequenceNum) {	
					System.out.println("Received in-order DATA packet");
					NUM_DATA_PACKETS_RECEIVED += 1;
				
					//start timer
					if (NUM_DATA_PACKETS_RECEIVED == 1) {
						startTime = System.nanoTime();
					}
					System.out.println("Number of data packets received:" + NUM_DATA_PACKETS_RECEIVED);
					//increase sequence number, reset to 1 if 65535 
					if (nextSequenceNum + NUM_PAYLOAD_BYTES <= 65535) {
						nextSequenceNum += NUM_PAYLOAD_BYTES;
					}
					else if (nextSequenceNum + NUM_PAYLOAD_BYTES > 65535) {
						nextSequenceNum = ((nextSequenceNum + NUM_PAYLOAD_BYTES) % 65535) + 1; 		
					}
		 
					//create DACK packet
					int ackNum = nextSequenceNum;
					ServerPacket dackPacket = new ServerPacket(0xcc, ackNum);
					byte[] dackByteArr = dackPacket.getByte();
			
					//set checksum bytes
					byte[] checksumBytes = checkSum(dackByteArr);
					dackByteArr[dackByteArr.length-2] = checksumBytes[0];
					dackByteArr[dackByteArr.length-1] = checksumBytes[1];
	
					//create DACK Datagram
					DatagramPacket dackDatagram = new DatagramPacket(dackByteArr, dackByteArr.length);
					dackDatagram.setAddress(dataDatagram.getAddress());
					dackDatagram.setPort(dataDatagram.getPort());
				
					System.out.println("Sending a DACK");
					//send DACK over UDP
					skt.send(dackDatagram);
				}
			
			//if packet passes integrity check but  has incorrect seq num 
			//resend last DACK packet
				else if (checkIntegrity(dataByteArr) && packetSequenceNum != nextSequenceNum) {
					System.out.println("Duplicate DATA packet received.");
					//create DACK packet
					int ackNum = nextSequenceNum;
					ServerPacket dackPacket = new ServerPacket(0xcc, ackNum);
					byte[] dackByteArr = dackPacket.getByte();
			
					//set checksum bytes
					byte[] checksumBytes = checkSum(dackByteArr);
					dackByteArr[dackByteArr.length-2] = checksumBytes[0];
					dackByteArr[dackByteArr.length-1] = checksumBytes[1];
				
					//create DACK Datagram
					DatagramPacket dackDatagram = new DatagramPacket(dackByteArr, dackByteArr.length);
					dackDatagram.setAddress(dataDatagram.getAddress());
					dackDatagram.setPort(dataDatagram.getPort());
				
					System.out.println("Resending DACK");
					//send DACK over UDP
					skt.send(dackDatagram);
				}
			}			
		}
		endTime = System.nanoTime();
		double transmissionTime = (endTime - startTime)/1000000.0;
		System.out.println("Communication was successful!!!");
		System.out.printf("Total tranmission time in ms: %.2f\n", transmissionTime); 
	}
}	
