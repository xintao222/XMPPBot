package chatbot;
import java.util.Arrays;
import java.util.Queue;
import java.util.Scanner;

import org.jivesoftware.smack.XMPPException;

import api.JabberSmackAPI;
import configuration.Conf;
import events.DisconnectInstruction;
import events.Event;
import events.GroupMessageReceived;
import events.RoomJoinInstruction;


public class Chatbot {
	
	JabberSmackAPI api;
	SimpleInteractor simpInt;
	
	static final int MAX_SLEEP_TIME = 1000;
	static final int INCR_SLEEP_TIME = 50;
	
	public void initialise() {
		api = new JabberSmackAPI();
		simpInt = new SimpleInteractor();
		
		try {
			System.out.println("Password: ");
			api.login(Conf.USERNAME, new Scanner(System.in).next());
		} catch (XMPPException e) {
			System.out.println("Error in initialising chatbot...\n");
			e.printStackTrace();
			System.exit(1);
		}
	}
	
	public void processEvents() {
		Queue<Event> events;
		Event event;
		int sleepTime = 0;
		
		while(true) {
			events = api.getEvents();
			
			if(events.isEmpty()) {
				if(sleepTime < MAX_SLEEP_TIME) sleepTime += INCR_SLEEP_TIME;
				try {
					Thread.sleep(sleepTime);
					continue;
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			} else {
				sleepTime = 0;
			}
			
			while(!events.isEmpty()) {
				event = events.remove();
				switch(event.getEventType()) {
				
				case GRP_MESSAGE_RCV:
					GroupMessageReceived msg = (GroupMessageReceived) event;
					processMessage(msg.getSender(), msg.getRoomName(),
							msg.getBody());
					break;
					
				case DISCONNECT_INSTR:
					api.disconnect();
					System.exit(0);
					
				case ROOM_JOIN_INSTR:
					RoomJoinInstruction instr = (RoomJoinInstruction) event;
					String newRoom = instr.getNewRoom();
					String currRoom = instr.getCurrRoom();
					String response;
					
					try {
						if(api.joinRoom(newRoom)) {
							response = newRoom + " successfully joined";
						} else {
							response = "I'm already in " + newRoom;
						}
					} catch (XMPPException e) {
						response = "Unable to connect to " + newRoom;
						e.printStackTrace();
					}
					
					try {
						api.sendRoomMessage(response, currRoom);
					} catch (XMPPException e) {
						e.printStackTrace();
					}
					
					break;
					
				default:
					System.out.println("Unrecognised event type: " +
						event.getEventType());
				}
			}
		}
	}
	
	private void processMessage(String sender, String roomName, String body) {
		String response = null;
		if(sender.equals(Conf.NICKNAME)) return;
		
		System.out.println(roomName + "/" + sender + ": " + body);
		
		// Respond to greetings.
		if(simpInt.checkGreeting(body) && simpInt.checkIdentity(body)) {
			response = simpInt.retGreeting(true) +
					simpInt.respondWithChance(" " + sender, 0.7) + " " +
					simpInt.retSmileEmote(0.5);
		
		// Respond to goodbyes.
		} else if(simpInt.checkGoodbye(body) && simpInt.checkIdentity(body)) {
			response = simpInt.retGoodbye(true) +
					simpInt.respondWithChance(" " + sender, 0.7) + " " +
					simpInt.retSmileEmote(0.5);
		
		// Respond to mood/wellbeing inquiries.
		} else if(simpInt.checkMoodInquiry(body)
				&& simpInt.checkIdentity(body)) {
			response = simpInt.retMood(true) + " " +
					simpInt.retThanks(false) +
					simpInt.respondWithChance(" " + sender, 0.8) + " " +
					simpInt.retSmileEmote(0.7);
		
		// Check for instruction to disconnect (currently from anybody).
		} else if(simpInt.checkPhraseInMessage("disconnect", body)
				&& simpInt.checkIdentity(body)) {
		
			Event disconnectInstr = new DisconnectInstruction();
			api.addEvent(disconnectInstr);
			response = "Disconnecting...";
		
		// Check for instruction to join a room (controller only).
		} else if(simpInt.checkPhraseInMessage("join", body)
				&& simpInt.checkIdentity(body)) {
			
			if(!checkController(sender)) {
				response = "Sorry " + sender + ", you're not authorised " +
						"to perform that action";
			} else {
				String[] parsedBody = simpInt.parseMessage(body);

				for(int i=0; i < parsedBody.length; i++) {
					if(parsedBody[i].equals("join")) {
						if(i == parsedBody.length - 1) {
							response = "Please restate instruction with " +
									"specified room";
							break;
						} else {
							String newRoom = parsedBody[i+1];
							Event RoomJoinInstruction =
									new RoomJoinInstruction(newRoom, roomName);
							api.addEvent(RoomJoinInstruction);
							response = "Attempting to join " + newRoom + "...";
							break;
						}
					}
				}
			}
			
		// Check if person is referencing this chatbot.
		} else if(simpInt.checkIdentity(body)) {
			response = simpInt.retInquiry(true);
			
		} else {
			simpInt.setIdentityCheck(false);
			
		}
		
		if(response != null) sendRoomMessage(response, roomName);
	}
	
	private boolean checkController(String username) {
		return Arrays.asList(Conf.MASTERS).contains(username);
	}

	public void joinRoom(String roomName) {
		try {
			api.joinRoom(roomName);
			String message = "Hey guys. I'm running for test purposes. If I " +
					"become a nuisance, just get my attention and say " +
					"'disconnect' to make me leave the chat " +
					simpInt.retSmileEmote(1);
			
			sendRoomMessage(message, roomName);
			System.out.println("Connected to " + roomName);
		} catch (XMPPException e) {
			System.out.println("Error in connecting chatbot to room...\n");
			e.printStackTrace();
			System.exit(1);
		}
	}
	
	public void sendRoomMessage(String message, String roomName) {
		try {
			api.sendRoomMessage(message, roomName);
		} catch (XMPPException e) {
			System.out.println("Error posting message in " + roomName);
			e.printStackTrace();
		}
	}
	
	public static void main(String args[]) {
		Chatbot mofichan = new Chatbot();
		
		mofichan.initialise();
		System.out.println("Mofichan connected");
		
		for(String roomName : Conf.ROOMS) {
			mofichan.joinRoom(roomName);
		}
		
		// While loop.
		mofichan.processEvents();
	}
}
