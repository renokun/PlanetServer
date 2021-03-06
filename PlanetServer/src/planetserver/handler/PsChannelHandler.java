package planetserver.handler;

import net.sf.json.JSONObject;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;

import planetserver.core.PsExtension;
import planetserver.network.PsObject;
import planetserver.requests.RequestType;
import planetserver.room.RoomManager;
import planetserver.session.SessionManager;
import planetserver.session.UserSession;
import planetserver.util.PsConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PsChannelHandler extends SimpleChannelHandler 
{
    private static final Logger logger = LoggerFactory.getLogger(PsChannelHandler.class);
    
    private RoomManager _roomManager;
    private SessionManager _sessionManager;
    private PsExtension _extension;

    public PsChannelHandler(PsExtension extension, SessionManager sessionManager, RoomManager roomManager)
    {
        _extension = extension;
        _roomManager = roomManager;
        _sessionManager = sessionManager;
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
    {
        UserSession session = new UserSession(e.getChannel());
        _sessionManager.registerSession(session);
        logger.debug("Session Added, Total Sessions: " + _sessionManager.getNumberOfSessions());
    }

    @Override
    public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e)
    {
        UserSession session = _sessionManager.getSession(e.getChannel());
        
        if (session != null)
        {
            _extension.logout(session);

            //disconnect the session!
            _sessionManager.unregisterSession(session);
            logger.debug("Session Removed, Total Sessions: " + _sessionManager.getNumberOfSessions());
        }
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
    { 
        UserSession session = _sessionManager.getSession(e.getChannel());
        
        if (session != null)
        {
            _extension.logout(session);

            _sessionManager.unregisterSession(session);
            logger.debug("Channel Closed, Total Sessions: " + _sessionManager.getNumberOfSessions());
        }
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception
    {
        String msg = (String)e.getMessage();
        String policyRequest = "<policy-file-request/>";

        UserSession session = _sessionManager.getSession(e.getChannel());

        if (msg.equalsIgnoreCase(policyRequest.trim()))
        {
            handlePolicyRequest(session);
        }
        else
        {
            JSONObject jsonObject = JSONObject.fromObject(msg);
            PsObject messageIn = new PsObject();
            messageIn.fromJsonObject(jsonObject);

            RequestType request = RequestType.get(messageIn.getInteger(PsConstants.REQUEST_TYPE));
     
            switch (request)
            {
                case EXTENSION:
                    _extension.handleClientRequest(session, messageIn);
                    break;
                    
                case PUBLICMESSAGE:
                    sendPublicMessage(session, messageIn);
                    break;
                    
                default:
                    _extension.handleEventRequest(request, session, messageIn);
            }  
        }
        //since this should be the last handler, no need to worry about other handlers upstream
        //super.messageReceived(ctx, e);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
    { 
        e.getCause().printStackTrace();
    }
    
    private void sendPublicMessage(UserSession user, PsObject obj)
    {
        if (user.isAuthenticated())
        {
            _roomManager.sendMessageToCurrentRoom(user, obj);
        }
    }
    
    private void handlePolicyRequest(UserSession user) 
    {
       String NEWLINE = "\r\n";  
       String policyString =   "<?xml version=\"1.0\"?>" + NEWLINE +
            "<!DOCTYPE cross-domain-policy SYSTEM \"/xml/dtds/cross-domain-policy.dtd\">" + NEWLINE +
            "" + NEWLINE +
            "<!-- Policy file for xmlsocket://socks.example.com -->" + NEWLINE +
            "<cross-domain-policy> " + NEWLINE +
            "" + NEWLINE +
            "   <!-- This is a master socket policy file -->" + NEWLINE +
            "   <!-- No other socket policies on the host will be permitted -->" + NEWLINE +
            "   <site-control permitted-cross-domain-policies=\"master-only\"/>" + NEWLINE +
            "" + NEWLINE +
            "   <!-- Instead of setting to-ports=\"*\", administrator's can use ranges and commas -->" + NEWLINE +
            "   <allow-access-from domain=\"*\" to-ports=\"*\" />" + NEWLINE +
            "" + NEWLINE +
            "</cross-domain-policy>" + NEWLINE;             
       
         //handles sending back the policy request!
         user.sendPolicy(policyString);
    }
}
