function show_circle(data)
%show_circle(data)
%   Draw a circle: data can be a circle matrix if 2x2 or [z,r] if 1x2
sz=size(data);
if sz(1)==1
	cent=data(1);
	rad=data(2);
else
	[cent,rad]=matrix2Circle(data);
end
	
theta = (0:2:360)*pi/180;

x = real(cent);
y = imag(cent);
x_circle = bsxfun(@times,rad,cos(theta));
x_circle = bsxfun(@plus,x_circle,x);
x_circle = cat(2,x_circle,nan(size(x_circle,1),1));
x_circle =  x_circle';
x_circle = x_circle(:);

y_circle = bsxfun(@times,rad,sin(theta));
y_circle = bsxfun(@plus,y_circle,y);
y_circle = cat(2,y_circle,nan(size(y_circle,1),1));
y_circle =  y_circle';
y_circle = y_circle(:);

% plot, maintain hold status
plot(x_circle,y_circle);
axis equal;
hold on;
end
