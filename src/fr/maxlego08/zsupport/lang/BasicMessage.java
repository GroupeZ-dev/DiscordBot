package fr.maxlego08.zsupport.lang;

public enum BasicMessage {

	COMMAND_NOT_FOUND(":x: Unable to find the requested command."),
	COMMAND_NO_CONSOLE(":x: You must be a player to be able to execute this command.."),
	COMMAND_NO_PLAYER(":x: Only the console can execute this command.."),
	COMMAND_NO_PERMISSION(":x: You do not have permission to execute this command.."),
	COMMAND_SYNTAX_ERROR(":x: You have to execute the command like this: %s"),
	
	VERIFY_ERROR(":x: Unable to add you find your account on https://groupez.dev/ please link your discord account to be able to make this command.\n\n\nUse ``/verify`` to verify your account."),
	VERIFY_ERROR_EMPTY(":x: You have not purchased any plugin.\n\n\nUse ``/verify`` to verify your account %channel%."),
	VERIFY_ERROR_ALREADY(":warning: You already have roles from your purchased plugins.\n\n\nUse ``/verify`` to verify your account."),
	
	SUGGESTION_NOT_FOUND("Aucune suggestion avec cet id n'a été trouvé."),
	SUGGESTION_NOT_FOUND2("Aucun message n'a été trouvé avec cet id !"),
	
	;

	private String message;

	private BasicMessage(String message) {
		this.message = message;
	}

	public String getMessage() {
		return message;
	}

	public String toMsg() {
		return message;
	}

	public String msg() {
		return message;
	}
	
}
