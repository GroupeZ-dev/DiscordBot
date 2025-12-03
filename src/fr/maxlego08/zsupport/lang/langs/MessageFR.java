package fr.maxlego08.zsupport.lang.langs;

import java.util.HashMap;
import java.util.Map;

import fr.maxlego08.zsupport.lang.Message;

public class MessageFR {

	public Map<Message, String> getMessages() {
		// FR
		Map<Message, String> frMessage = new HashMap<>();
		frMessage.put(Message.TICKET_ALREADY_CREATE,
				"**Attention!** %user% tu as déjé créé un ticket ici, inutile d'enfaire un autre.");

		frMessage.put(Message.TICKET_ALREADY_CREATE_REPLY, "Vous avez déjé un ticket ouvert: %s");

		frMessage.put(Message.TICKET_DESC,
				"Bienvenue dans votre ticket, un membre de l'équipe  viendra répondre é vos besoins sous peu.");
		frMessage.put(Message.TICKET_PLUGIN_CHOOSE, "Clique sur le plugin pour lequel tu souhaites obtenir de l'aide:");
		frMessage.put(Message.TICKET_PLUGIN, "Vous venez de choisir le plugin **%s** %s.");
		frMessage.put(Message.TICKET_PLUGIN_ROLE,
				"Vous avez déjé confirmé l'achat du plugin, vous pouvez directement poser votre question.");
		frMessage.put(Message.TICKET_PLUGIN_ROLE_ERROR,
				"Vous n'avez pas confirmé l'achat du plugin. Une verification est en cours.");
		frMessage.put(Message.TICKET_PLUGIN_ROLE_ERROR_ID,
				"Vous devez envoyer **uniquement** l'id de la transaction pour qu'une vérification soit faite.");
		frMessage.put(Message.TICKET_PLUGIN_ROLE_ID_SUCCESS,
				"Votre demande a bien été prise en compte, un administrateur va rapidement vérifier le paiement.");
		frMessage.put(Message.TICKET_PLUGIN_ROLE_SUCCESS, "Vous venez de reéevoir le réle pour le plugin %plugin%");
		frMessage.put(Message.TICKET_PLUGIN_ROLE_ERROR_GIVE,
				"Impossible de prouver que vous avez acheter le plugin %plugin%.");

		frMessage.put(Message.TICKET_CREATE_ERROR,
				":x: Impossible de créer votre ticket, veuillez vérifier que votre compte discord est correctement relié au site.\nhttps://groupez.dev/dashboard/account");
		frMessage.put(Message.TICKET_CREATE_SUCCESS, "Votre ticket est disponible ici : ");
		frMessage.put(Message.TICKET_CREATE_WAIT,
				"Vérification de la liaison de votre compte discord en cours, veuillez patienter.");

		frMessage.put(Message.TICKET_WELCOME, "Bienvenue %s");
		frMessage.put(Message.TICKET_PLUGIN_ERROR, "Une erreur est survenue, merci de contacter l'équipe.");

		frMessage.put(Message.TICKET_CLOSE_BUTTON, "Fermer le ticket");

		frMessage.put(Message.TICKET_OTHER, "Veuillez détailler la demande de votre ticket.");
		frMessage.put(Message.TICKET_CLOSE, "Fermure du ticket dans %s seconde%s");

		frMessage.put(Message.OTHER, "Autre");
		frMessage.put(Message.TICKET_OTHER_INFO, "Veuillez choisir le plugin pour lequelle vous avez besoin d'aide.");

		frMessage.put(Message.TICKET_ORDER,
				"Pour pouvoir passer une commande vous devez donner toutes les informations sur le projet." + "\n"
						+ "Comme par exemple:" + "\n" + "- Le nom de votre serveur" + "\n"
						+ "- La version de votre serveur" + "\n" + "- Le délais de livraison" + "\n"
						+ "- La description compléte de votre plugin" + "\n" + "\n" + "Informations:" + "\n"
						+ "Tarif: **20é/h**" + "\n"
						+ "Achat du code source: **50%** du prix (par exemple, pour un plugin é 100é les sources seront vendu é 50é)"
						+ "\n" + "Siret: 88761749600013");

		frMessage.put(Message.TICKET_CHOOSE,
				"Veuillez choisir le type de votre ticket:" + "\n" + "\n" + ":dollar: Pour passer une commande" + "\n"
						+ ":question: Pour demander de l'aide sur un plugin" + "\n"
						+ "%s Pour demander l'accés sur spigot" + "\n" + ":wave: Questions avant achat" + "\n");

		frMessage.put(Message.TICKET_CHOOSE_ORDER, "Passer une commande");
		frMessage.put(Message.TICKET_CHOOSE_PLUGIN, "Support plugins");
		frMessage.put(Message.TICKET_CHOOSE_SPIGOT, "Demander l'accés sur spigot");
		frMessage.put(Message.TICKET_CHOOSE_BEFORE_PURCHASE, "Questions avant achat");

		frMessage.put(Message.TICKET_SPIGOT,
				"Pour demander l'accés vous devez fournir le nom de votre compte spigot ainsi que la liste des plugins ou vous voulez l'accés.");

		frMessage.put(Message.TICKET_HOUR,
				":exclamation: Attention, il est actuellement **%s:%s** en france."
						+ " Le support est disponible uniquement entre **9 heures** et **20 heures** tous les jours."
						+ " Cependant le support se livre le droit d'étre moins actif durant les weekends.");

		frMessage.put(Message.TICKET_PLUGIN_VERSION_COMMAND,
				"Pour accélérer le support, veuillez écrire la version du plugin." + "\n"
						+ "Pour obtenir la version du plugin faite la commande suivante: **/%s version**" + "\n" + "\n"
						+ "Par exemple, dans l'image ci-dessous, la version est 3.1.0.7, vous devez donc écrire juste la version: ``3.1.0.7``");

		frMessage.put(Message.TICKET_PLUGIN_VERSION_CONSOLE,
				"Pour accélérer le support, veuillez écrire la version du plugin." + "\n"
						+ "Pour obtenir la version du plugin regardez dans la console." + "\n" + "\n"
						+ "Par exemple, dans l'image ci-dessous, la version est 3.1.0.7, vous devez donc écrire juste la version: ``3.1.0.7``");

		frMessage.put(Message.TICKET_PLUGIN_VERSION_READ,
				":arrow_up: Veuillez correctement lire le message du dessus et suivre les instructions ! Vous pourrez écrire la version du plugin dans **15 secondes**.");

		frMessage.put(Message.TICKET_PLUGIN_VERSION_ERROR,
				":x: Vous n'utilisez pas la derniére version du plugin." + "\n"
						+ "Mettez à jour le plugin, vérifier si votre probléme est toujours présent." + "\n"
						+ "Si oui, alors vous devez écrire correctement la bonne version du plugin.");

		frMessage.put(Message.TICKET_PLUGIN_RULES, "\n"
				+ "Merci de ne pas **mentionner** l'équipe de GroupeZ sous peine de sanction." + "\n"
				+ "N'oubliez pas de désactiver les mentions lorsque vous faites une réponse !" + "\n" + "\n"
				+ "Si votre ticket ne reéoit aucun message au bout de **4 jours**, il sera automatiquement fermé !");

		frMessage.put(Message.TICKET_QUESTION,
				"Vous pouvez poser des questions avant l'achat d'un plugin. Vous ne pouvez pas demander du support dans ce ticket.");

		frMessage.put(Message.TICKET_PLUGIN_INFO,
				"Pour aider la prise en charge du support, veuillez donner un maximum d'informations:" + "\n"
						+ "- La version du plugin" + "\n" + "- La version de votre serveur" + "\n"
						+ "- La liste de vos plugins" + "\n" + "- Les logs, si vous avez des erreurs" + "\n"
						+ "- Vos fichiers de configuration, si vous avez un probléme de configuration" + "\n\n"
						+ "Pour partager vos fichiers oé vos logs vous pouvez utiliser https://pastebin.com/");

		return frMessage;
	}

}
