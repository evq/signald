/**
 * Copyright (C) 2018 Finn Herzfeld
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.finn.signald;

import io.finn.signald.BuildConfig;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.Security;
import java.util.concurrent.ConcurrentHashMap;

import org.newsclub.net.unix.AFUNIXServerSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.sentry.Sentry;

public class Main {

  private static final Logger logger = LogManager.getLogger("signald");

  public static void main(String[] args) {
    logger.info("Starting " + BuildConfig.NAME + " " + BuildConfig.VERSION);
    try {
      Sentry.init();
      Sentry.getContext().addExtra("release", BuildConfig.VERSION);
      Sentry.getContext().addExtra("signal_url", BuildConfig.SIGNAL_URL);
      Sentry.getContext().addExtra("signal_cdn_url", BuildConfig.SIGNAL_CDN_URL);

      String bind_addr = System.getenv("SIGNALD_BIND_ADDR");

      String socket_path = "/var/run/signald/signald.sock";
      if(args.length > 0) {
        socket_path = args[0];
      }

      // Workaround for BKS truststore
      Security.insertProviderAt(new org.bouncycastle.jce.provider.BouncyCastleProvider(), 1);

      SocketManager socketmanager = new SocketManager();
      ConcurrentHashMap<String,Manager> managers = new ConcurrentHashMap<String,Manager>();
      ConcurrentHashMap<String,MessageReceiver> receivers = new ConcurrentHashMap<String,MessageReceiver>();

      ServerSocket server;
      if (bind_addr != null) {
        URI uri = new URI("tcp://" + bind_addr); // may throw URISyntaxException
        String host = uri.getHost();
        int port = uri.getPort();
        if (uri.getHost() == null || uri.getPort() == -1) {
          throw new URISyntaxException(uri.toString(), "SIGNALD_BIND_ADDR must be HOST:PORT");
        }

        logger.info("Listening on " + bind_addr);
        server = new ServerSocket(port, 0, InetAddress.getByName(host));
      } else {
        logger.info("Listening on " + socket_path);
        // Spins up one thread per inbound connection to the control socket
        server = AFUNIXServerSocket.newInstance();
        server.bind(new AFUNIXSocketAddress(new File(socket_path)));
      }

      // Spins up one thread per registered signal number, listens for incoming messages
      String settingsPath = System.getProperty("user.home") + "/.config/signal";

      File[] users = new File(settingsPath + "/data").listFiles();

      if(users == null) {
        logger.warn("No users are currently defined, you'll need to register or link to your existing signal account");
      }

      while (!Thread.interrupted()) {
        try {
          Socket socket = server.accept();
          socketmanager.add(socket);

          // Kick off the thread to read input
          Thread socketHandlerThread = new Thread(new SocketHandler(socket, receivers, managers), "socketlistener");
          socketHandlerThread.start();

        } catch(IOException e) {
          logger.catching(e);
        }
      }
    } catch(Exception e) {
      logger.catching(e);
      System.exit(1);
    }
  }
}
