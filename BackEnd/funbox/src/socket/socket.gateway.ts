import {
  ConnectedSocket,
  MessageBody,
  OnGatewayConnection,
  SubscribeMessage,
  WebSocketGateway,
  WebSocketServer,
} from '@nestjs/websockets';
import { Server, Socket } from 'socket.io';
import { SocketService } from './socket.service';
import {
  ParseIntPipe,
  UseFilters,
  UsePipes,
  ValidationPipe,
} from '@nestjs/common';
import { UpdateLocationDto } from './dto/update-location.dto';
import { SocketUserDto } from './dto/socket-user.dto';
import { WsExceptionFilter } from './filters/ws-exception.filter';

@WebSocketGateway({
  namespace: 'socket',
  cors: { origin: '*' },
})
@UseFilters(WsExceptionFilter)
export class SocketGateway implements OnGatewayConnection {
  @WebSocketServer()
  private server: Server;

  constructor(private socketService: SocketService) {}

  handleConnection(client: Socket): void {
    this.socketService.handleConnection(client);
  }

  @SubscribeMessage('updateLocation')
  @UsePipes(ValidationPipe)
  updateLocation(client: Socket, updateLocationDto: UpdateLocationDto): void {
    const { locX, locY } = updateLocationDto;
    client.data.locX = locX;
    client.data.locY = locY;

    this.server.emit('location', JSON.stringify(SocketUserDto.of(client)));
  }

  @SubscribeMessage('applyQuizGame')
  applyQuizGame(
    @ConnectedSocket() client: Socket,
    @MessageBody(ParseIntPipe) opponentId: number,
  ): void {
    this.socketService.makeGameRoom(client, opponentId);
  }
}
